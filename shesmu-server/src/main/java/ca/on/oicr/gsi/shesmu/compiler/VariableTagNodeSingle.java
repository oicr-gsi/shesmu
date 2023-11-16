package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.IntConsumer;
import org.objectweb.asm.Type;

public class VariableTagNodeSingle extends VariableTagNode {

  private static final Type A_STRING_TYPE = Type.getType(String.class);

  protected VariableTagNodeSingle(ExpressionNode expression) {
    super(expression);
  }

  @Override
  public Optional<IntConsumer> renderDynamicSize(Renderer renderer) {
    return Optional.empty();
  }

  @Override
  protected String decorateEcma(String data) {
    return data;
  }

  @Override
  public int renderStaticTag(Renderer renderer, int tagIndex) {
    renderer.methodGen().dup();
    renderer.methodGen().push(tagIndex);
    render(renderer);
    renderer.methodGen().arrayStore(A_STRING_TYPE);
    return 1;
  }

  @Override
  protected Imyhat requiredType() {
    return Imyhat.STRING;
  }

  @Override
  public int staticSize() {
    return 1;
  }
}
