package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class ListNodeWithExpression extends ListNode {

  protected final ExpressionNode expression;

  protected ListNodeWithExpression(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  /**
   * Add all free variable names to the set provided.
   */
  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final Set<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toSet());
    expression.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  protected abstract void finishMethod(Renderer renderer);

  protected abstract Pair<Renderer, LoadableConstructor> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables);

  public abstract DestructuredArgumentNode nextName(DestructuredArgumentNode inputs);

  @Override
  public abstract Ordering order(Ordering previous, Consumer<String> errorHandler);

  @Override
  public final LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Pair<Renderer, LoadableConstructor> result =
        makeMethod(
            builder,
            name,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));

    result.first().methodGen().visitCode();
    expression.render(result.first());
    finishMethod(result.first());
    result.first().methodGen().returnValue();
    result.first().methodGen().visitMaxs(0, 0);
    result.first().methodGen().visitEnd();
    return result.second();
  }

  private Set<String> definedNames;
  /** Resolve all variable plugins in this expression and its children. */
  @Override
  public final Optional<DestructuredArgumentNode> resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    if (expression.resolve(defs.bind(name), errorHandler)) {
      final DestructuredArgumentNode nextNames = nextName(name);
      definedNames = nextNames.targets().map(Target::name).collect(Collectors.toSet());
      return Optional.of(nextNames);

    } else {
      return Optional.empty();
    }
  }

  /** Resolve all functions plugins in this expression */
  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & resolveExtraDefinitions(expressionCompilerServices, errorHandler);
  }

  protected abstract boolean resolveExtraDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  @Override
  public final Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler)
        ? typeCheckExtra(incoming, errorHandler)
        : Optional.empty();
  }

  /** Perform type checking on this expression. */
  protected abstract Optional<Imyhat> typeCheckExtra(
      Imyhat incoming, Consumer<String> errorHandler);
}
