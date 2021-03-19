package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;
import static org.objectweb.asm.Type.INT_TYPE;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class DiscriminatorNodeUnivalued extends DiscriminatorNodeBaseManipulated {
  private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_ITERATOR__NEXT =
      new Method("next", A_OBJECT_TYPE, new Type[] {});
  private static final Method METHOD_SET__ITERATOR =
      new Method("iterator", A_ITERATOR_TYPE, new Type[] {});
  private static final Method METHOD_SET__SIZE = new Method("size", INT_TYPE, new Type[] {});
  private final int column;
  private final int line;
  private Imyhat type = Imyhat.BAD;

  public DiscriminatorNodeUnivalued(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    super(expression, name);
    this.line = line;
    this.column = column;
  }

  @Override
  protected Imyhat convertType(Imyhat original, Consumer<String> errorHandler) {
    if (original instanceof Imyhat.ListImyhat) {
      type = ((Imyhat.ListImyhat) original).inner();
      return type;
    } else {
      errorHandler.accept(
          String.format("%d:%d: Expected a list type but got %s.", line, column, original.name()));
      return Imyhat.BAD;
    }
  }

  @Override
  protected void render(Renderer renderer) {
    renderer.methodGen().dup();
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__SIZE);
    renderer.methodGen().push(1);
    final var end = renderer.methodGen().newLabel();
    renderer.methodGen().ifICmp(GeneratorAdapter.EQ, end);
    renderer.methodGen().visitInsn(Opcodes.ACONST_NULL);
    renderer.methodGen().returnValue();
    renderer.methodGen().mark(end);
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ITERATOR);
    renderer.methodGen().invokeInterface(A_ITERATOR_TYPE, METHOD_ITERATOR__NEXT);
    renderer.methodGen().unbox(type.apply(TO_ASM));
  }
}
