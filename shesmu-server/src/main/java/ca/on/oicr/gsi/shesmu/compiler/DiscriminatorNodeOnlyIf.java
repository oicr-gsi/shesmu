package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class DiscriminatorNodeOnlyIf extends DiscriminatorNodeBaseManipulated {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__sF_PRESENT =
      new Method("isPresent", BOOLEAN_TYPE, new Type[] {});
  private final int column;
  private final int line;
  private Imyhat type = Imyhat.BAD;

  public DiscriminatorNodeOnlyIf(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    super(expression, name);
    this.line = line;
    this.column = column;
  }

  @Override
  protected Imyhat convertType(Imyhat original, Consumer<String> errorHandler) {
    if (original instanceof Imyhat.OptionalImyhat) {
      type = ((Imyhat.OptionalImyhat) original).inner();
      return type;
    }
    errorHandler.accept(
        String.format("%d:%d: Expected optional type but got %s.", line, column, original.name()));
    return Imyhat.BAD;
  }

  @Override
  protected void render(Renderer renderer) {
    renderer.methodGen().dup();
    renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__sF_PRESENT);
    final Label end = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.NE, end);
    renderer.methodGen().visitInsn(Opcodes.ACONST_NULL);
    renderer.methodGen().returnValue();
    renderer.methodGen().mark(end);
    renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__GET);
    renderer.methodGen().unbox(type.apply(TO_ASM));
  }
}
