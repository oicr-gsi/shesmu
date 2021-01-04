package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class TupleElementNodeRest extends TupleElementNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private Imyhat.TupleImyhat tuple;

  protected TupleElementNodeRest(ExpressionNode expression) {
    super(expression);
  }

  @Override
  public int render(Renderer renderer, int start) {
    expression.render(renderer);
    final int local = renderer.methodGen().newLocal(A_TUPLE_TYPE);
    renderer.methodGen().storeLocal(local);
    for (int index = 0; index < tuple.count(); index++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(start + index);
      renderer.methodGen().loadLocal(local);
      renderer.methodGen().push(index);
      renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    return start + tuple.count();
  }

  @Override
  public String render(EcmaScriptRenderer renderer) {
    final String value = expression.renderEcma(renderer);
    return IntStream.range(0, tuple.count()).mapToObj(i -> value + "[" + i + "]").collect(Collectors.joining(", "));
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    if (expression.type() instanceof Imyhat.TupleImyhat) {
      tuple = (Imyhat.TupleImyhat) expression.type();
      return true;
    }
    expression.typeError("tuple", expression.type(), errorHandler);
    return false;
  }

  @Override
  public Stream<Imyhat> types() {
    return tuple.inner();
  }
}
