package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class TupleElementNodeSingle extends TupleElementNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  protected TupleElementNodeSingle(ExpressionNode expression) {
    super(expression);
  }

  @Override
  public int render(Renderer renderer, int start) {
    renderer.methodGen().dup();
    renderer.methodGen().push(start);
    expression.render(renderer);
    renderer.methodGen().valueOf(expression.type().apply(TO_ASM));
    renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    return start + 1;
  }

  @Override
  public String render(EcmaScriptRenderer renderer) {
    return expression.renderEcma(renderer);
  }

  @Override
  public Stream<Imyhat> types() {
    return Stream.of(expression.type());
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
