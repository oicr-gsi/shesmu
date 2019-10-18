package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeTupleGet extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});

  private final ExpressionNode expression;

  private final int index;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeTupleGet(int line, int column, ExpressionNode expression, int index) {
    super(line, column);
    this.expression = expression;
    this.index = index;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.mark(line());

    renderer.methodGen().push(index);
    renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
    renderer.methodGen().unbox(type.apply(TypeUtils.TO_ASM));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      final Imyhat expressionType = expression.type();
      if (expressionType instanceof Imyhat.TupleImyhat) {
        final Imyhat.TupleImyhat tupleType = (Imyhat.TupleImyhat) expressionType;
        type = tupleType.get(index);
        if (type.isBad()) {
          errorHandler.accept(
              String.format("%d:%d: Cannot access tuple at index %d.", line(), column(), index));
          ok = false;
        }
      } else {
        ok = false;
        typeError("tuple", expressionType, errorHandler);
      }
    }
    return ok;
  }
}
