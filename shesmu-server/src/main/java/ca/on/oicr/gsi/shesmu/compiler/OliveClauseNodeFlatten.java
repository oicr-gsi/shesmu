package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseNodeFlatten extends OliveClauseNode {
  private final int column;
  private final ExpressionNode expression;
  private List<Target> incoming;
  private final int line;
  private final DestructuredArgumentNode name;
  private Imyhat unrollType;

  public OliveClauseNodeFlatten(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.expression = expression;
    name.setFlavour(Target.Flavour.STREAM);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> inputs = new TreeSet<>();
    expression.collectFreeVariables(inputs, Target.Flavour::isStream);
    return Stream.of(
        new OliveClauseRow(
            "Flatten",
            line,
            column,
            true,
            false,
            name.targets()
                .map(
                    t ->
                        new VariableInformation(
                            t.name(),
                            t.type(),
                            inputs.stream(),
                            VariableInformation.Behaviour.DEFINITION))));
  }

  @Override
  public OliveNode.ClauseStreamOrder ensureRoot(
      OliveNode.ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == OliveNode.ClauseStreamOrder.PURE) {
      expression.collectFreeVariables(signableNames, Target.Flavour.STREAM_SIGNABLE::equals);
      // Okay, we're going to lie here. Flatten does modify the stream but we're going to say that
      // it doesn't for an important reason: signatures. We want the variables that cross flatten to
      // still be part of the signature since flatten only adds new (non-signable) variables. For
      // this reason, we'll mark flatten as non-deadly (i.e., it won't erase previously defined
      // variables).
      return OliveNode.ClauseStreamOrder.PURE;
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
    expression.collectFreeVariables(freeVariables, Target.Flavour::needsCapture);

    final FlattenBuilder flattenBuilder =
        oliveBuilder.flatten(
            line,
            column,
            unrollType,
            oliveBuilder
                .loadableValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    flattenBuilder.add(name::render);
    incoming.forEach(flattenBuilder::add);

    flattenBuilder.explodeMethod().methodGen().visitCode();
    expression.render(flattenBuilder.explodeMethod());
    flattenBuilder.explodeMethod().methodGen().returnValue();
    flattenBuilder.explodeMethod().methodGen().endMethod();

    flattenBuilder.finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    incoming = defs.stream().filter(t -> t.flavour().isStream()).collect(Collectors.toList());
    final Set<String> existingNames =
        incoming.stream().map(Target::name).collect(Collectors.toSet());
    boolean ok =
        name.targets()
                .filter(
                    target -> {
                      if (existingNames.contains(target.name())) {
                        errorHandler.accept(
                            String.format(
                                "%d:%d: Variable “%s” already exists. Maybe use “Let” to reshape the data before “Flatten” or choose a different name.",
                                line, column, target.name()));
                        return true;
                      }
                      return false;
                    })
                .count()
            == 0;
    return defs.replaceStream(
        Stream.concat(incoming.stream().map(Target::wrap), name.targets()),
        expression.resolve(defs, errorHandler) & name.resolve(oliveCompilerServices, errorHandler)
            && ok);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(String.format("%d:%d: Assignment discards value.", line, column));
    } else if (expression.typeCheck(errorHandler)) {
      if (expression.type() instanceof Imyhat.ListImyhat) {
        this.unrollType = ((Imyhat.ListImyhat) expression.type()).inner();
        return name.typeCheck(unrollType, errorHandler);
      } else {
        expression.typeError("list", expression.type(), errorHandler);
      }
    }
    return false;
  }
}
