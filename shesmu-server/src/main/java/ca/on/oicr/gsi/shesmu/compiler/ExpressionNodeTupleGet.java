package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeTupleGet extends ExpressionNode {
  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_OPTIONAL__FLAT_MAP =
      new Method("flatMap", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_OPTIONAL__MAP =
      new Method("map", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});

  private final ExpressionNode expression;

  private final int index;
  private boolean lifted;
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
    if (lifted) {
      final LambdaBuilder lambda =
          new LambdaBuilder(
              renderer.root(),
              String.format("Optional Getter %d:%d %d", line(), column(), index),
              LambdaBuilder.function(A_OBJECT_TYPE, A_TUPLE_TYPE));
      final GeneratorAdapter method = lambda.methodGen();
      method.visitCode();
      method.loadArg(0);
      renderLoad(method);
      method.returnValue();
      method.endMethod();
      lambda.push(renderer);
      if (type instanceof Imyhat.OptionalImyhat || type == Imyhat.NOTHING) {
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__FLAT_MAP);
      } else {
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__MAP);
      }

    } else {
      renderLoad(renderer.methodGen());
      renderer.methodGen().unbox(type.apply(TypeUtils.TO_ASM));
    }
  }

  private void renderLoad(GeneratorAdapter method) {
    method.push(index);
    method.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
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
    return lifted ? type.asOptional() : type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      Imyhat expressionType = expression.type();
      if (expressionType instanceof Imyhat.OptionalImyhat) {
        lifted = true;
        expressionType = ((Imyhat.OptionalImyhat) expressionType).inner();
      }
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
