package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.OptionalImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.TupleImyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeSwizzle extends ExpressionNode {
  private enum Access {
    TUPLE {
      @Override
      public void render(ExpressionNodeSwizzle owner, Renderer renderer) {
        owner.renderTupleCopy(renderer.methodGen(), () -> owner.source.render(renderer));
      }

      @Override
      public String renderEcma(ExpressionNodeSwizzle owner, EcmaScriptRenderer renderer) {
        final var obj = renderer.newConst(owner.source.renderEcma(renderer));
        return owner.fieldNames.stream()
            .map(f -> obj + "." + f)
            .collect(Collectors.joining(",", "[", "]"));
      }

      @Override
      public Imyhat type(TupleImyhat tuple) {
        return tuple;
      }
    },
    LIFTED_TUPLE {
      @Override
      public void render(ExpressionNodeSwizzle owner, Renderer renderer) {
        owner.source.render(renderer);
        final var builder =
            new LambdaBuilder(
                renderer.root(),
                String.format("Swizzle %d:%d", owner.line(), owner.column()),
                LambdaBuilder.function(A_TUPLE_TYPE, A_TUPLE_TYPE));
        builder.push(renderer);
        renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__MAP);

        final var lambdaBody = builder.methodGen();
        lambdaBody.visitCode();
        owner.renderTupleCopy(lambdaBody, () -> lambdaBody.loadArg(0));
        lambdaBody.returnValue();
        lambdaBody.endMethod();
      }

      @Override
      public String renderEcma(ExpressionNodeSwizzle owner, EcmaScriptRenderer renderer) {
        return String.format(
            "$runtime.mapNull(%s, v => %s)",
            owner.source.renderEcma(renderer),
            owner.fieldNames.stream()
                .map(f -> "v." + f)
                .collect(Collectors.joining(",", "[", "]")));
      }

      @Override
      public Imyhat type(TupleImyhat tuple) {
        return tuple.asOptional();
      }
    };

    public abstract void render(ExpressionNodeSwizzle owner, Renderer renderer);

    public abstract String renderEcma(ExpressionNodeSwizzle owner, EcmaScriptRenderer renderer);

    public abstract Imyhat type(TupleImyhat tuple);
  }

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method CTOR_TUPLE =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  private static final Method METHOD_OPTIONAL__MAP =
      new Method("map", A_OPTIONAL_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private Access access;
  private final List<String> fieldNames;
  private final int[] indices;
  private final ExpressionNode source;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeSwizzle(
      int line, int column, ExpressionNode source, List<String> fieldNames) {
    super(line, column);
    this.source = source;
    this.fieldNames = fieldNames;
    indices = new int[fieldNames.size()];
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    source.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    source.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    access.render(this, renderer);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return access.renderEcma(this, renderer);
  }

  private void renderTupleCopy(GeneratorAdapter methodGen, Runnable loadSource) {
    methodGen.newInstance(A_TUPLE_TYPE);
    methodGen.dup();
    methodGen.push(fieldNames.size());
    methodGen.newArray(A_OBJECT_TYPE);

    loadSource.run();

    var target = 0;
    for (final var source : indices) {
      methodGen.dup2();
      methodGen.push(source);
      methodGen.invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
      methodGen.push(target++);
      methodGen.swap();
      methodGen.arrayStore(A_OBJECT_TYPE);
    }
    methodGen.pop();
    methodGen.invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return source.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return source.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (source.typeCheck(errorHandler)) {
      var sourceType = source.type();
      if (sourceType instanceof OptionalImyhat) {
        sourceType = ((OptionalImyhat) sourceType).inner();
        access = Access.LIFTED_TUPLE;
      } else {
        access = Access.TUPLE;
      }
      if (sourceType instanceof ObjectImyhat) {
        final var fieldTypes = (ObjectImyhat) sourceType;
        final var resultTypes = new Imyhat[fieldNames.size()];
        var ok = true;
        var index = 0;
        for (final var field : fieldNames) {
          final var i = index++;
          final var fieldType = resultTypes[i] = fieldTypes.get(field);
          if (fieldType.isBad()) {
            ok = false;
            errorHandler.accept(
                String.format("%d:%d: Field “%s” is not in object", line(), column(), field));
          } else {
            indices[i] = fieldTypes.index(field);
          }
        }
        type = access.type(Imyhat.tuple(resultTypes));
        return ok;
      } else {
        typeError("object", sourceType, errorHandler);
      }
    }
    return false;
  }
}
