package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
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
  protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
    return builder.sort(line(), column(), name(), expression.type(), loadables);
  }

  @Override
  public String nextName() {
    return name();
  }

  @Override
  public Imyhat nextType() {
    return parameter.type();
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous == Ordering.BAD ? Ordering.BAD : Ordering.REQESTED;
  }

  @Override
  protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    if (expression.type().isOrderable()) {
      return true;
    }
    errorHandler.accept(
        String.format(
            "%d:%d: Expected comparable type for sorting but got %s.",
            line(), column(), expression.type().name()));
    return false;
  }
}
