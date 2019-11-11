package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class LetArgumentNodeUnivalued extends LetArgumentNodeBaseExpression {
  private static final Type A_ITERATOR_TYPE = Type.getType(Iterator.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_ITERATOR__NEXT =
      new Method("next", A_OBJECT_TYPE, new Type[] {});
  private static final Method METHOD_SET__ITERATOR =
      new Method("iterator", A_ITERATOR_TYPE, new Type[] {});
  private static final Method METHOD_SET__SIZE = new Method("size", Type.INT_TYPE, new Type[] {});

  public LetArgumentNodeUnivalued(DestructuredArgumentNode name, ExpressionNode expression) {
    super(name, expression);
  }

  @Override
  public boolean filters() {
    return true;
  }

  @Override
  public Consumer<Renderer> render(LetBuilder let, Imyhat type, Consumer<Renderer> loadLocal) {
    let.checkAndSkip(
        (r, happyPath) -> {
          loadLocal.accept(r);
          r.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__SIZE);
          r.methodGen().push(1);
          r.methodGen().ifICmp(GeneratorAdapter.EQ, happyPath);
        });
    return r -> {
      loadLocal.accept(r);
      r.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__ITERATOR);
      r.methodGen().invokeInterface(A_ITERATOR_TYPE, METHOD_ITERATOR__NEXT);
      r.methodGen().unbox(((Imyhat.ListImyhat) type).inner().apply(TO_ASM));
    };
  }

  @Override
  public boolean typeCheck(
      int line,
      int column,
      Imyhat type,
      DestructuredArgumentNode name,
      Consumer<String> errorHandler) {
    if (type == Imyhat.EMPTY) {
      errorHandler.accept(String.format("%d:%d: Row will always be dropped.", line, column));
      return false;
    }
    if (type instanceof Imyhat.ListImyhat) {
      return name.typeCheck(((Imyhat.ListImyhat) type).inner(), errorHandler);
    }
    errorHandler.accept(
        String.format("%d:%d: Expected list but got %s.", line, column, type.name()));
    return false;
  }
}
