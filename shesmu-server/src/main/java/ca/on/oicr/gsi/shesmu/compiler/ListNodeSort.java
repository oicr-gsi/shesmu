package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;

public class ListNodeSort extends ListNodeWithExpression {
  public ListNodeSort(int line, int column, ExpressionNode expression) {
    super(line, column, expression);
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    renderer.methodGen().valueOf(expression.type().apply(TypeUtils.TO_ASM));
  }

  @Override
  protected Pair<Renderer, LoadableConstructor> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables) {
    return new Pair<>(builder.sort(line(), column(), name, expression.type(), loadables), name);
  }

  @Override
  public DestructuredArgumentNode nextName(DestructuredArgumentNode inputs) {
    return inputs;
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous == Ordering.BAD ? Ordering.BAD : Ordering.REQESTED;
  }

  @Override
  protected boolean resolveExtraDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    if (expression.type().isOrderable()) {
      return Optional.of(incoming);
    }
    errorHandler.accept(
        String.format(
            "%d:%d: Expected comparable type for sorting but got %s.",
            line(), column(), expression.type().name()));
    return Optional.empty();
  }
}
