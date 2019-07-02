package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseNodeLet extends OliveClauseNode {

  private final List<LetArgumentNode> arguments;
  private final int column;
  private final int line;

  public OliveClauseNodeLet(int line, int column, List<LetArgumentNode> arguments) {
    super();
    this.line = line;
    this.column = column;
    this.arguments = arguments;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            "Let",
            line,
            column,
            false,
            true,
            arguments
                .stream()
                .map(
                    arg -> {
                      final Set<String> inputs = new HashSet<>();
                      arg.collectFreeVariables(inputs, Flavour::isStream);
                      return new VariableInformation(
                          arg.name(), arg.type(), inputs.stream(), Behaviour.DEFINITION);
                    })));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE) {
      arguments
          .stream()
          .forEach(a -> a.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      return ClauseStreamOrder.TRANSFORMED;
    }
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Map<String, OliveDefineBuilder> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    arguments.forEach(
        argument -> argument.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final LetBuilder let =
        oliveBuilder.let(
            line,
            column,
            oliveBuilder
                .loadableValues()
                .filter(loadable -> freeVariables.contains(loadable.name()))
                .toArray(LoadableValue[]::new));
    arguments.forEach(argument -> argument.render(let));
    let.finish();
  }

  @Override
  public NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler) {
    final boolean good =
        arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
            == arguments.size();
    return defs.replaceStream(arguments.stream().map(x -> x), good);
  }

  @Override
  public boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler) {
    boolean ok =
        arguments
                .stream()
                .filter(argument -> argument.resolveFunctions(definedFunctions, errorHandler))
                .count()
            == arguments.size();
    if (arguments.stream().map(LetArgumentNode::name).distinct().count() != arguments.size()) {
      ok = false;
      final Set<String> allItems = new HashSet<>();
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variables in “Let” clause: %s",
              line,
              column,
              arguments
                  .stream()
                  .map(LetArgumentNode::name)
                  .filter(n -> !allItems.add(n))
                  .sorted()
                  .collect(Collectors.joining(", "))));
    }

    return ok;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count()
        == arguments.size();
  }
}
