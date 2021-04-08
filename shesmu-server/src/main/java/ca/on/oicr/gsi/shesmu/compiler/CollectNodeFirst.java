package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class CollectNodeFirst extends CollectNodeOptional {

  protected CollectNodeFirst(int line, int column, ExpressionNode selector) {
    super(line, column, selector);
  }

  @Override
  protected void finishMethod(Renderer renderer) {}

  @Override
  protected Renderer makeMethod(
      JavaStreamBuilder builder,
      LoadableConstructor name,
      Imyhat returnType,
      LoadableValue[] loadables) {
    final var map = builder.map(line(), column(), name, returnType, loadables);
    builder.first();
    return map;
  }

  @Override
  public boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    if (ordering == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: Items to First are in random order. Results will not be reproducible. Sort first.",
              line(), column()));
      return false;
    }
    return true;
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, selector.type(), selector::renderEcma);
    return String.format("$runtime.nullifyUndefined(%s[0])", builder.finish());
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
