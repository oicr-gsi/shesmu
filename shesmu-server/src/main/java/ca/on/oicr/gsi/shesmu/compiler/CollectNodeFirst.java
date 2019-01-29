package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class CollectNodeFirst extends CollectNodeWithDefault {

  protected CollectNodeFirst(
      int line, int column, ExpressionNode selector, ExpressionNode alternative) {
    super("First", line, column, selector, alternative);
  }

  @Override
  protected void finishMethod(Renderer renderer) {}

  @Override
  protected Pair<Renderer, Renderer> makeMethod(
      JavaStreamBuilder builder, LoadableValue[] loadables) {
    final Renderer map = builder.map(line(), column(), name(), type(), loadables);
    final Renderer alternative = builder.first(line(), column(), type(), loadables);
    return new Pair<>(map, alternative);
  }

  @Override
  protected Imyhat returnType(Imyhat incomingType, Imyhat selectorType) {
    return selectorType;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
