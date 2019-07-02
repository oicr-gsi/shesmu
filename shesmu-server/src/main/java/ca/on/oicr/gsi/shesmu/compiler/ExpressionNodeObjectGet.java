package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeObjectGet extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});

  private final ExpressionNode expression;

  private final String field;

  private int index = -1;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeObjectGet(int line, int column, ExpressionNode expression, String field) {
    super(line, column);
    this.expression = expression;
    this.field = field;
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
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
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
      if (expressionType instanceof Imyhat.ObjectImyhat) {
        final Imyhat.ObjectImyhat objectType = (Imyhat.ObjectImyhat) expressionType;
        type = objectType.get(field);
        if (type.isBad()) {
          errorHandler.accept(
              String.format("%d:%d: Cannot access field at %s.", line(), column(), field));
          ok = false;
        } else {
          index = objectType.index(field);
        }
      } else {
        ok = false;
        typeError("object", expressionType, errorHandler);
      }
    }
    return ok;
  }
}
