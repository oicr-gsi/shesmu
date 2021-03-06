package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public class OliveClauseNodeWhere extends OliveClauseNode {

  private final int column;
  private final ExpressionNode expression;
  private final Optional<String> label;
  private final int line;

  public OliveClauseNodeWhere(
      Optional<String> label, int line, int column, ExpressionNode expression) {
    this.label = label;
    this.line = line;
    this.column = column;
    this.expression = expression;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
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
    expression.collectFreeVariables(inputs, Flavour::isStream);
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Where"),
            line,
            column,
            true,
            false,
            inputs.stream()
                .map(
                    n ->
                        new VariableInformation(
                            n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER))));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      expression.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
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
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);

    final var filter =
        oliveBuilder.filter(
            line,
            column,
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
    filter.methodGen().visitCode();
    expression.render(filter);
    filter.methodGen().returnValue();
    filter.methodGen().visitMaxs(0, 0);
    filter.methodGen().visitEnd();

    oliveBuilder.measureFlow(line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    return defs.fail(expression.resolve(defs, errorHandler));
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final var ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (!expression.type().isSame(Imyhat.BOOLEAN)) {
        errorHandler.accept(
            String.format(
                "%d:%d: Expression is “Where” clause must be boolean, got %s.",
                line, column, expression.type().name()));
        return false;
      }
    }
    return ok;
  }
}
