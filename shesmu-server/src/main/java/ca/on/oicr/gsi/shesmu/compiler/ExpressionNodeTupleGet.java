package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeTupleGet extends ExpressionNode {
  private enum Access {
    BAD {
      @Override
      public void render(int line, int column, Renderer renderer, Imyhat type, int index) {
        throw new UnsupportedOperationException();
      }

      @Override
      public String render(EcmaScriptRenderer renderer, String renderEcma, int index) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Imyhat output(Imyhat type) {
        return Imyhat.BAD;
      }
    },
    TUPLE {
      @Override
      public void render(int line, int column, Renderer renderer, Imyhat type, int index) {
        renderLoad(renderer.methodGen(), index);
        renderer.methodGen().unbox(type.apply(TypeUtils.TO_ASM));
      }

      @Override
      public String render(EcmaScriptRenderer renderer, String value, int index) {
        return value + "[" + index + "]";
      }

      @Override
      public Imyhat output(Imyhat type) {
        return type;
      }
    },
    LIFTED_TUPLE {
      @Override
      public void render(int line, int column, Renderer renderer, Imyhat type, int index) {
        final LambdaBuilder lambda =
            new LambdaBuilder(
                renderer.root(),
                String.format("Optional Getter %d:%d %d", line, column, index),
                LambdaBuilder.function(A_OBJECT_TYPE, A_TUPLE_TYPE));
        final GeneratorAdapter method = lambda.methodGen();
        method.visitCode();
        method.loadArg(0);
        renderLoad(method, index);
        method.returnValue();
        method.endMethod();
        lambda.push(renderer);
        if (type instanceof Imyhat.OptionalImyhat || type == Imyhat.NOTHING) {
          renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__FLAT_MAP);
        } else {
          renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__MAP);
        }
      }

      @Override
      public String render(EcmaScriptRenderer renderer, String value, int index) {
        return String.format("$runtime.mapNull(%s, v => v[%d])", value, index);
      }

      @Override
      public Imyhat output(Imyhat type) {
        return type.asOptional();
      }
    },
    MAP {
      @Override
      public void render(int line, int column, Renderer renderer, Imyhat type, int index) {
        renderer.methodGen().push(index);
        renderer.methodGen().cast(Type.INT_TYPE, Type.LONG_TYPE);
        renderer.methodGen().valueOf(Type.LONG_TYPE);
        if (type.asOptional().isSame(type)) {
          renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
          renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET_OR_DEFAULT);
        } else {
          renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET);
          renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF_NULLABLE);
        }
      }

      @Override
      public String render(EcmaScriptRenderer renderer, String value, int index) {
        return String.format("$runtime.nullifyUndefined(%s.get(%d))", value, index);
      }

      @Override
      public Imyhat output(Imyhat type) {
        return type.asOptional();
      }
    },
    LIFTED_MAP {
      @Override
      public void render(int line, int column, Renderer renderer, Imyhat type, int index) {
        final LambdaBuilder builder =
            new LambdaBuilder(
                renderer.root(),
                String.format("Dictionary Access %d:%d", line, column),
                LambdaBuilder.function(type.asOptional(), A_MAP_TYPE));
        builder.push(renderer);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__FLAT_MAP);

        final GeneratorAdapter lambdaBody = builder.methodGen();
        lambdaBody.visitCode();
        lambdaBody.loadArg(0);
        lambdaBody.push(index);
        lambdaBody.cast(Type.INT_TYPE, Type.LONG_TYPE);
        lambdaBody.valueOf(Type.LONG_TYPE);
        if (type.asOptional().isSame(type)) {
          lambdaBody.invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
          lambdaBody.invokeInterface(A_MAP_TYPE, METHOD_MAP__GET_OR_DEFAULT);
        } else {
          lambdaBody.invokeInterface(A_MAP_TYPE, METHOD_MAP__GET);
          lambdaBody.invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF_NULLABLE);
        }
        lambdaBody.returnValue();
        lambdaBody.endMethod();
      }

      @Override
      public String render(EcmaScriptRenderer renderer, String value, int index) {
        return String.format("$runtime.mapNull(%s, v => $runtime.nullifyUndefined(v.get(%d)))", value, index);
      }
      @Override
      public Imyhat output(Imyhat type) {
        return type.asOptional();
      }
    };

    public abstract Imyhat output(Imyhat type);

    public abstract void render(int line, int column, Renderer renderer, Imyhat type, int index);

    public abstract String render(EcmaScriptRenderer renderer, String value, int index);
  }

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_MAP__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_MAP__GET_OR_DEFAULT =
      new Method("getOrDefault", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[0]);
  private static final Method METHOD_OPTIONAL__FLAT_MAP =
      new Method("flatMap", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_OPTIONAL__MAP =
      new Method("map", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_OPTIONAL__OF_NULLABLE =
      new Method("ofNullable", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});

  private static void renderLoad(GeneratorAdapter method, int index) {
    method.push(index);
    method.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
  }

  private Access access = Access.BAD;
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
  public Optional<String> dumpColumnName() {
    return expression.dumpColumnName().map(s -> s + "[" + index + "]");
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return access.render(renderer, expression.renderEcma(renderer), index);
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.mark(line());
    access.render(line(), column(), renderer, type, index);
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
    return access.output(type);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      Imyhat expressionType = expression.type();
      boolean lifted = false;
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
        access = lifted ? Access.LIFTED_TUPLE : Access.TUPLE;
      } else if (expressionType instanceof Imyhat.DictionaryImyhat) {
        final Imyhat.DictionaryImyhat mapType = (Imyhat.DictionaryImyhat) expressionType;
        if (mapType.key().isSame(Imyhat.INTEGER)) {
          type = mapType.value();
          access = lifted ? Access.LIFTED_MAP : Access.MAP;
        } else {
          ok = false;
          typeError(Imyhat.INTEGER, mapType.key(), errorHandler);
        }
      } else {
        ok = false;
        typeError("map or tuple", expressionType, errorHandler);
      }
    }
    return ok;
  }
}
