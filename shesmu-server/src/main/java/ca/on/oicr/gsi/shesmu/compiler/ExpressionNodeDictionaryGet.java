package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeDictionaryGet extends ExpressionNode {
  private enum Access {
    BAD {
      @Override
      public void render(
          int line, int column, Renderer renderer, ExpressionNode index, boolean resultIsOptional) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Imyhat output(Imyhat type) {
        return Imyhat.BAD;
      }
    },
    MAP {
      @Override
      public void render(
          int line, int column, Renderer renderer, ExpressionNode index, boolean resultIsOptional) {
        index.render(renderer);
        if (resultIsOptional) {
          renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
          renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET_OR_DEFAULT);
        } else {
          renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET);
          renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF_NULLABLE);
        }
      }

      @Override
      public Imyhat output(Imyhat type) {
        return type.asOptional();
      }
    },
    LIFTED_MAP {
      @Override
      public void render(
          int line, int column, Renderer renderer, ExpressionNode index, boolean resultIsOptional) {
        final Set<String> captures = new HashSet<>();
        index.collectFreeVariables(captures, Flavour::needsCapture);
        final LambdaBuilder builder =
            new LambdaBuilder(
                renderer.root(),
                String.format("Dictionary Access %d:%d", line, column),
                LambdaBuilder.function(A_OPTIONAL_TYPE, A_MAP_TYPE),
                renderer.streamType(),
                renderer
                    .allValues()
                    .filter(v -> captures.contains(v.name()))
                    .toArray(LoadableValue[]::new));
        builder.push(renderer);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__FLAT_MAP);

        final Renderer lambdaBody = builder.renderer(renderer.signerEmitter());
        lambdaBody.methodGen().visitCode();
        lambdaBody.methodGen().loadArg(builder.trueArgument(0));
        index.render(lambdaBody);
        if (resultIsOptional) {
          lambdaBody.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
          lambdaBody.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET_OR_DEFAULT);
        } else {
          lambdaBody.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__GET);
          lambdaBody.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF_NULLABLE);
        }
        lambdaBody.methodGen().returnValue();
        lambdaBody.methodGen().endMethod();
      }

      @Override
      public Imyhat output(Imyhat type) {
        return type.asOptional();
      }
    };

    public abstract Imyhat output(Imyhat type);

    public abstract void render(
        int line, int column, Renderer renderer, ExpressionNode index, boolean resultIsOptional);
  }

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_MAP__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE});
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
  private static final Method METHOD_MAP__GET_OR_DEFAULT =
      new Method("getOrDefault", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});

  private Access access = Access.BAD;
  private final ExpressionNode expression;
  private final ExpressionNode index;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeDictionaryGet(
      int line, int column, ExpressionNode expression, ExpressionNode index) {
    super(line, column);
    this.expression = expression;
    this.index = index;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
    index.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    index.collectPlugins(pluginFileNames);
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.mark(line());
    access.render(line(), column(), renderer, index, type.asOptional().isSame(type));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler) & index.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & index.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return access.output(type);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler) & index.typeCheck(errorHandler);
    if (ok) {
      Imyhat expressionType = expression.type();
      boolean lifted = false;
      if (expressionType instanceof Imyhat.OptionalImyhat) {
        lifted = true;
        expressionType = ((Imyhat.OptionalImyhat) expressionType).inner();
      }
      if (expressionType instanceof Imyhat.TupleImyhat) {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot access tuple using an expression. Only fixed numbers are allowed.",
                line(), column()));
        ok = false;
      } else if (expressionType instanceof Imyhat.DictionaryImyhat) {
        final Imyhat.DictionaryImyhat mapType = (Imyhat.DictionaryImyhat) expressionType;
        if (mapType.key().isSame(index.type())) {
          type = mapType.value();
          access = lifted ? Access.LIFTED_MAP : Access.MAP;
        } else {
          ok = false;
          typeError(mapType.key(), index.type(), errorHandler);
        }
      } else {
        ok = false;
        typeError("map", expressionType, errorHandler);
      }
    }
    return ok;
  }
}
