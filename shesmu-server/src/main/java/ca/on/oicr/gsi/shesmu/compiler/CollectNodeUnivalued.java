package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class CollectNodeUnivalued extends CollectNodeOptional {

  protected CollectNodeUnivalued(int line, int column, ExpressionNode selector) {
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
    final Renderer map = builder.map(line(), column(), name, returnType, loadables);
    builder.univalued();
    return map;
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    return String.format(
        "$runtime.univalued(%s, %s)",
        builder.finish(),
        builder
            .renderer()
            .lambda(
                2,
                (r, args) ->
                    selector
                        .type()
                        .apply(EcmaScriptRenderer.isEqual(args.apply(0), args.apply(1)))));
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
