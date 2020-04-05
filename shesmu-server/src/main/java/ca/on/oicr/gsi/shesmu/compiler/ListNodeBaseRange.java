package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ListNodeBaseRange extends ListNode {

  private final ExpressionNode expression;

  protected ListNodeBaseRange(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public final Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot apply a range limit to an unordered stream.", line(), column()));
      return Ordering.BAD;
    }
    return previous;
  }

  @Override
  public final LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    render(builder, expression::render);
    return name;
  }

  protected abstract void render(JavaStreamBuilder builder, Consumer<Renderer> expression);

  @Override
  public final Optional<DestructuredArgumentNode> resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler) ? Optional.of(name) : Optional.empty();
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public final Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    final boolean ok = expression.typeCheck(errorHandler);
    if (ok && !expression.type().isSame(Imyhat.INTEGER)) {
      expression.typeError(Imyhat.INTEGER, expression.type(), errorHandler);
      return Optional.empty();
    }
    return Optional.of(incoming);
  }
}
