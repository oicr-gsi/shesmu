package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public final class CollectNodeOptima extends CollectNodeWithDefault {

  private final boolean max;

  public CollectNodeOptima(
      int line, int column, boolean max, ExpressionNode selector, ExpressionNode alternate) {
    super(max ? "Max" : "Min", line, column, selector, alternate);
    this.max = max;
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    renderer.methodGen().valueOf(selector.type().apply(TypeUtils.TO_ASM));
  }

  @Override
  protected Pair<Renderer, Renderer> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables) {
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
