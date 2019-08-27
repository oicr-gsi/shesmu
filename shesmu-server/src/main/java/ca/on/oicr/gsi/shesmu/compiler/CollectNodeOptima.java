package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public final class CollectNodeOptima extends CollectNodeOptional {

  private final boolean max;

  public CollectNodeOptima(int line, int column, boolean max, ExpressionNode selector) {
    super(line, column, selector);
    this.max = max;
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    renderer.methodGen().valueOf(selector.type().apply(TypeUtils.TO_ASM));
  }

  @Override
  protected Renderer makeMethod(
      JavaStreamBuilder builder,
      LoadableConstructor name,
      Imyhat returnType,
      LoadableValue[] loadables) {
    return builder.optima(line(), column(), max, name, selector.type(), loadables);
  }

  @Override
  protected Imyhat returnType(Imyhat incomingType, Imyhat selectorType) {
    return incomingType;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    if (selector.type().isOrderable()) {
      return true;
    }
    errorHandler.accept(
        String.format(
            "%d:%d: Expected orderable type, but got %s.", line(), column(), type().name()));
    return false;
  }
}
