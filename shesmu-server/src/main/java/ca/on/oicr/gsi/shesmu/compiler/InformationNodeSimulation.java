package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.commons.GeneratorAdapter;

public class InformationNodeSimulation extends InformationNode {
  private final int column;
  private final List<ObjectElementNode> constants;
  private ExpressionCompilerServices expressionCompilerServices;
  private final int line;
  private DefinitionRepository nativeDefinitions;
  private final List<RefillerDefinitionNode> refillers;
  private final ProgramNode script;
  private final String scriptRaw;

  public InformationNodeSimulation(
      int line,
      int column,
      List<ObjectElementNode> constants,
      List<RefillerDefinitionNode> refillers,
      String scriptRaw,
      ProgramNode script) {
    this.line = line;
    this.column = column;
    this.constants = constants;
    this.scriptRaw = scriptRaw;
    this.script = script;
    this.refillers = refillers;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    try {
      return String.format(
          "{type: \"simulation\", script: %s, parameters: %s, fakeRefillers: %s}",
          RuntimeSupport.MAPPER.writeValueAsString(scriptRaw),
          constants.stream()
              .flatMap(e -> e.renderConstant(renderer))
              .sorted()
              .collect(Collectors.joining(", ", "{", "}")),
          refillers.stream()
              .map(RefillerDefinitionNode::render)
              .sorted()
              .collect(Collectors.joining(", ", "{", "}")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final var duplicates =
        constants.stream()
            .flatMap(ObjectElementNode::names)
            .collect(Collectors.groupingBy(Pair::first, Collectors.counting()));
    var ok = true;
    for (final var duplicate : duplicates.entrySet()) {
      if (duplicate.getValue() > 1) {
        ok = false;
        errorHandler.accept(
            String.format(
                "%d:%d: Constant %s is repeated %d times.",
                line, column, duplicate.getKey(), duplicate.getValue()));
      }
    }
    return constants.stream().filter(c -> c.resolve(defs, errorHandler)).count() == constants.size()
        && ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    this.expressionCompilerServices = expressionCompilerServices;
    this.nativeDefinitions = nativeDefinitions;
    return constants.stream()
                .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == constants.size()
        & refillers.stream()
                .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == refillers.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final var availableConstants =
        Stream.concat(
                nativeDefinitions.constants(),
                constants.stream()
                    .flatMap(ObjectElementNode::names)
                    .map(
                        p ->
                            new ConstantDefinition(
                                String.join(
                                    Parser.NAMESPACE_SEPARATOR, "shesmu", "simulated", p.first()),
                                p.second(),
                                null,
                                null) {
                              @Override
                              public void load(GeneratorAdapter methodGen) {
                                throw new UnsupportedOperationException();
                              }

                              @Override
                              public String load() {
                                throw new UnsupportedOperationException();
                              }
                            }))
            .collect(Collectors.toList());
    final var localRefillerNames =
        refillers.stream().map(RefillerDefinitionNode::name).collect(Collectors.toSet());
    final var availableRefillers =
        Stream.concat(
                refillers.stream(),
                nativeDefinitions.refillers().filter(r -> !localRefillerNames.contains(r.name())))
            .collect(Collectors.toMap(RefillerDefinition::name, Function.identity(), (a, b) -> a));
    if (constants.stream().filter(c -> c.typeCheck(errorHandler)).count() == constants.size()) {
      return script.validate(
          expressionCompilerServices::inputFormat,
          nativeDefinitions
                  .functions()
                  .collect(
                      Collectors.toMap(FunctionDefinition::name, Function.identity(), (a, b) -> a))
              ::get,
          nativeDefinitions
                  .actions()
                  .collect(
                      Collectors.toMap(ActionDefinition::name, Function.identity(), (a, b) -> a))
              ::get,
          availableRefillers::get,
          nativeDefinitions
                  .oliveDefinitions()
                  .collect(
                      Collectors.toMap(CallableDefinition::name, Function.identity(), (a, b) -> a))
              ::get,
          errorHandler,
          availableConstants::stream,
          nativeDefinitions::signatures,
          false,
          true);
    } else {
      return false;
    }
  }
}
