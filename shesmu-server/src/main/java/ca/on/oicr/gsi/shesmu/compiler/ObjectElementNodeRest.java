package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ObjectElementNodeRest extends ObjectElementNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private final List<String> exceptions;
  private Imyhat.ObjectImyhat tuple;

  protected ObjectElementNodeRest(ExpressionNode expression, List<String> exceptions) {
    super(expression);
    this.exceptions = exceptions;
  }

  @Override
  public Stream<Pair<String, Imyhat>> names() {
    return tuple
        .fields()
        .filter(field -> !exceptions.contains(field.getKey()))
        .map(e -> new Pair<>(e.getKey(), e.getValue().first()));
  }

  @Override
  public void render(Renderer renderer, ToIntFunction<String> indexOf) {
    expression.render(renderer);
    final int local = renderer.methodGen().newLocal(A_TUPLE_TYPE);
    renderer.methodGen().storeLocal(local);
    tuple
        .fields()
        .filter(field -> !exceptions.contains(field.getKey()))
        .forEach(
            field -> {
              renderer.methodGen().dup();
              renderer.methodGen().push(indexOf.applyAsInt(field.getKey()));
              renderer.methodGen().loadLocal(local);
              renderer.methodGen().push(field.getValue().second());
              renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
              renderer.methodGen().arrayStore(A_OBJECT_TYPE);
            });
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    if (expression.type() instanceof Imyhat.ObjectImyhat) {
      tuple = (Imyhat.ObjectImyhat) expression.type();
      return true;
    }
    expression.typeError("tuple", expression.type(), errorHandler);
    return false;
  }
}
