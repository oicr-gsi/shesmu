package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class LetArgumentNodeOptional extends LetArgumentNodeBaseExpression {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Method METHOD_OPTIONAL__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__IS_PRESENT =
      new Method("isPresent", Type.BOOLEAN_TYPE, new Type[] {});

  public LetArgumentNodeOptional(DestructuredArgumentNode name, ExpressionNode expression) {
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
          r.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__IS_PRESENT);
          r.methodGen().ifZCmp(GeneratorAdapter.NE, happyPath);
        });
    return r -> {
      loadLocal.accept(r);
      r.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__GET);
      r.methodGen().unbox(((Imyhat.OptionalImyhat) type).inner().apply(TO_ASM));
    };
  }

  @Override
  public boolean typeCheck(
      int line,
      int column,
      Imyhat type,
      DestructuredArgumentNode name,
      Consumer<String> errorHandler) {
    if (type == Imyhat.NOTHING) {
      errorHandler.accept(String.format("%d:%d: Row will always be dropped.", line, column));
      return false;
    }
    if (type instanceof Imyhat.OptionalImyhat) {
      return name.typeCheck(((Imyhat.OptionalImyhat) type).inner(), errorHandler);
    }
    errorHandler.accept(
        String.format("%d:%d: Expected optional but got %s.", line, column, type.name()));
    return false;
  }
}
