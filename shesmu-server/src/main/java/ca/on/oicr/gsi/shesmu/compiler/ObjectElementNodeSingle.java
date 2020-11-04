package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class ObjectElementNodeSingle extends ObjectElementNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private final String field;

  protected ObjectElementNodeSingle(String field, ExpressionNode expression) {
    super(expression);
    this.field = field;
  }

  @Override
  public Stream<Pair<String, Imyhat>> names() {
    return Stream.of(new Pair<>(field, expression.type()));
  }

  @Override
  public Stream<String> render(EcmaScriptRenderer renderer) {
    return Stream.of(String.format("%s: %s", field, expression.renderEcma(renderer)));
  }

  @Override
  public void render(Renderer renderer, ToIntFunction<String> indexOf) {
    renderer.methodGen().dup();
    renderer.methodGen().push(indexOf.applyAsInt(field));
    expression.render(renderer);
    renderer.methodGen().valueOf(expression.type().apply(TO_ASM));
    renderer.methodGen().arrayStore(A_OBJECT_TYPE);
  }

  @Override
  public Stream<String> renderConstant(EcmaScriptRenderer renderer) {
    return Stream.of(
        String.format(
            "%s: {type: \"%s\", value: %s}",
            field, expression.type().descriptor(), expression.renderEcma(renderer)));
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
