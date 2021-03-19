package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.UnivaluedCollector;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Start;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Subsampler;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/** Helper to build bytecode for “olives” (decision-action stanzas) */
public final class JavaStreamBuilder {
  public interface RenderSubsampler {
    void render(Renderer renderer, int previousLocal, Imyhat streamType, LoadableConstructor name);
  }

  private static final Type A_BIFUNCTION_TYPE = Type.getType(BiFunction.class);
  private static final Type A_BINARY_OPERATOR_TYPE = Type.getType(BinaryOperator.class);
  private static final Type A_COLLECTORS_TYPE = Type.getType(Collectors.class);
  private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
  private static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_START_TYPE = Type.getType(Start.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Type A_SUBSAMPLER_TYPE = Type.getType(Subsampler.class);
  private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);
  private static final Type A_UNIVALUED_COLLECTOR_TYPE = Type.getType(UnivaluedCollector.class);
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_COLLECTORS__TO_MAP =
      new Method(
          "toMap",
          A_COLLECTOR_TYPE,
          new Type[] {A_FUNCTION_TYPE, A_FUNCTION_TYPE, A_BINARY_OPERATOR_TYPE, A_SUPPLIER_TYPE});

  private static final Method METHOD_COLLECTORS__TO_SET =
      new Method("toSet", A_COLLECTOR_TYPE, new Type[] {});
  private static final Method METHOD_COMPARATOR__COMPARING =
      new Method("comparing", A_COMPARATOR_TYPE, new Type[] {A_FUNCTION_TYPE});

  private static final Method METHOD_OPTIONAL__OR_ELSE_GET =
      new Method("orElseGet", A_OBJECT_TYPE, new Type[] {A_SUPPLIER_TYPE});
  private static final Method METHOD_RUNTIME_SUPPORT__COLLECT_OBJECT =
      new Method(
          "collect",
          Type.getType(Tuple.class),
          new Type[] {A_STREAM_TYPE, Type.getType(Function[].class)});
  private static final Method METHOD_STREAM__COLLECT =
      new Method("collect", A_OBJECT_TYPE, new Type[] {A_COLLECTOR_TYPE});
  private static final Method METHOD_STREAM__COUNT =
      new Method("count", Type.LONG_TYPE, new Type[] {});
  private static final Method METHOD_STREAM__DISTINCT =
      new Method("distinct", A_STREAM_TYPE, new Type[] {});
  private static final Method METHOD_STREAM__FILTER =
      new Method("filter", A_STREAM_TYPE, new Type[] {A_PREDICATE_TYPE});
  private static final Method METHOD_STREAM__FIND_FIRST =
      new Method("findFirst", A_OPTIONAL_TYPE, new Type[] {});
  private static final Method METHOD_STREAM__FLAT_MAP =
      new Method("flatMap", A_STREAM_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_STREAM__LIMIT =
      new Method("limit", A_STREAM_TYPE, new Type[] {Type.LONG_TYPE});
  private static final Method METHOD_STREAM__MAP =
      new Method("map", A_STREAM_TYPE, new Type[] {A_FUNCTION_TYPE});
  private static final Method METHOD_STREAM__MAX =
      new Method("max", A_OPTIONAL_TYPE, new Type[] {A_COMPARATOR_TYPE});
  private static final Method METHOD_STREAM__MIN =
      new Method("min", A_OPTIONAL_TYPE, new Type[] {A_COMPARATOR_TYPE});
  private static final Method METHOD_STREAM__REDUCE =
      new Method(
          "reduce",
          A_OBJECT_TYPE,
          new Type[] {A_OBJECT_TYPE, A_BIFUNCTION_TYPE, A_BINARY_OPERATOR_TYPE});
  private static final Method METHOD_STREAM__REVERSE =
      new Method("reverse", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE});
  private static final Method METHOD_STREAM__SKIP =
      new Method("skip", A_STREAM_TYPE, new Type[] {Type.LONG_TYPE});
  private static final Method METHOD_STREAM__SORTED =
      new Method("sorted", A_STREAM_TYPE, new Type[] {A_COMPARATOR_TYPE});
  private static final Method METHOD_SUBSAMPLER__SUBSAMPLE =
      new Method("subsample", A_STREAM_TYPE, new Type[] {A_STREAM_TYPE});
  private static final Method METHOD_UNIVALUED_COLLECTOR__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_SUPPLIER_TYPE});
  private Imyhat currentType;

  private final RootBuilder owner;

  private final Renderer renderer;

  public JavaStreamBuilder(RootBuilder owner, Renderer renderer, Imyhat initialType) {
    this.owner = owner;
    this.renderer = renderer;
    currentType = initialType;
  }

  public void collect() {
    renderer.loadImyhat(currentType.descriptor());
    renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_COLLECTORS__TO_SET);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().checkCast(A_SET_TYPE);
  }

  public JavaStreamBuilder[] collectObject(
      int line, int column, List<Pair<String, LoadableValue[]>> children) {
    final var builders = new JavaStreamBuilder[children.size()];
    renderer.methodGen().push(children.size());
    renderer.methodGen().newArray(A_FUNCTION_TYPE);
    for (var i = 0; i < children.size(); i++) {
      final var lambda =
          new LambdaBuilder(
              renderer.root(),
              String.format("Object %d:%d %s", line, column, children.get(i).first()),
              LambdaBuilder.function(A_STREAM_TYPE, A_OBJECT_TYPE),
              renderer.streamType(),
              children.get(i).second());
      renderer.methodGen().dup();
      renderer.methodGen().push(i);
      lambda.push(renderer);
      renderer.methodGen().arrayStore(A_FUNCTION_TYPE);
      final var inner = lambda.renderer(renderer.signerEmitter());
      inner.methodGen().visitCode();
      inner.methodGen().loadArg(lambda.trueArgument(0));
      builders[i] = inner.buildStream(currentType);
    }
    renderer
        .methodGen()
        .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__COLLECT_OBJECT);
    return builders;
  }

  public void collector(Type resultType, Consumer<Renderer> loadCollector) {
    loadCollector.accept(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().checkCast(resultType);
  }

  private Renderer comparator(
      int line,
      int column,
      String syntax,
      LoadableConstructor name,
      Imyhat targetType,
      LoadableValue... capturedVariables) {
    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ %s %d:%d ⚖️", syntax, line, column),
            LambdaBuilder.function(targetType.apply(TypeUtils.TO_BOXED_ASM), currentType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.invokeInterfaceStatic(A_COMPARATOR_TYPE, METHOD_COMPARATOR__COMPARING);
    return makeRender(builder, name);
  }

  public void count() {
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COUNT);
  }

  public Imyhat currentType() {
    return currentType;
  }

  public Pair<Renderer, Renderer> dictionary(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat keyType,
      Imyhat valueType,
      LoadableValue[] capturedVariables) {
    final var keyBuilder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Dictionary %d:%d 🔑️", line, column),
            LambdaBuilder.function(keyType, currentType),
            renderer.streamType(),
            capturedVariables);
    final var valueBuilder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Dictionary %d:%d️", line, column),
            LambdaBuilder.function(valueType, currentType),
            renderer.streamType(),
            capturedVariables);
    keyBuilder.push(renderer);
    valueBuilder.push(renderer);
    renderer
        .methodGen()
        .getStatic(A_RUNTIME_SUPPORT_TYPE, "USELESS_BINARY_OPERATOR", A_BINARY_OPERATOR_TYPE);
    renderer.loadImyhat(currentType.descriptor());
    LambdaBuilder.pushVirtual(
        renderer, "newMap", LambdaBuilder.supplier(A_MAP_TYPE), A_IMYHAT_TYPE);
    renderer.methodGen().invokeStatic(A_COLLECTORS_TYPE, METHOD_COLLECTORS__TO_MAP);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().checkCast(A_MAP_TYPE);

    return new Pair<>(makeRender(keyBuilder, name), makeRender(valueBuilder, name));
  }

  public void distinct() {
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__DISTINCT);
  }

  public final Renderer filter(
      int line, int column, LoadableConstructor name, LoadableValue... capturedVariables) {
    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Where %d:%d", line, column),
            LambdaBuilder.predicate(currentType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
    return makeRender(builder, name);
  }

  public void first() {
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FIND_FIRST);
  }

  public final Renderer flatten(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat newType,
      LoadableValue... capturedVariables) {
    final var oldType = currentType;
    currentType = newType;

    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Flatten %d:%d", line, column),
            LambdaBuilder.function(A_STREAM_TYPE, oldType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FLAT_MAP);
    return makeRender(builder, name);
  }

  public void limit(Consumer<Renderer> limitProducer) {
    limitProducer.accept(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__LIMIT);
  }

  private Renderer makeRender(LambdaBuilder builder, LoadableConstructor name) {
    final var output = builder.renderer(renderer.signerEmitter());
    final var argCount = output.methodGen().getArgumentTypes().length;
    name.create(r -> r.methodGen().loadArg(argCount - 1)).forEach(v -> output.define(v.name(), v));
    return output;
  }

  public final Renderer map(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat newType,
      LoadableValue... capturedVariables) {
    final var oldType = currentType;
    currentType = newType;

    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Map %d:%d", line, column),
            LambdaBuilder.function(newType, oldType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
    return makeRender(builder, name);
  }

  public final Renderer mapToPrimitive(
      int line,
      int column,
      LoadableConstructor name,
      PrimitiveStream primitive,
      LoadableValue... capturedVariables) {
    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Map %d:%d", line, column),
            primitive.lambdaOf(currentType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer
        .methodGen()
        .invokeInterface(
            A_STREAM_TYPE,
            new Method(
                primitive.methodName(),
                primitive.outputStreamType(),
                new Type[] {builder.lambda().interfaceType()}));
    return makeRender(builder, name);
  }

  public final Renderer match(
      int line,
      int column,
      Match matchType,
      LoadableConstructor name,
      LoadableValue... capturedVariables) {
    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ %s %d:%d", matchType.syntax(), line, column),
            LambdaBuilder.predicate(currentType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, matchType.method);
    return makeRender(builder, name);
  }

  public Renderer optima(
      int line,
      int column,
      boolean max,
      LoadableConstructor name,
      Imyhat targetType,
      LoadableValue... capturedVariables) {
    final var extractRenderer =
        comparator(line, column, max ? "Max" : "Min", name, targetType, capturedVariables);

    renderer
        .methodGen()
        .invokeInterface(A_STREAM_TYPE, max ? METHOD_STREAM__MAX : METHOD_STREAM__MIN);

    return extractRenderer;
  }

  public Renderer reduce(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat accumulatorType,
      LoadableConstructor accumulatorName,
      Consumer<Renderer> initial,
      LoadableValue... capturedVariables) {

    final var builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Reduce %d:%d", line, column),
            LambdaBuilder.bifunction(accumulatorType, accumulatorType, currentType),
            renderer.streamType(),
            capturedVariables);
    initial.accept(renderer);
    renderer.methodGen().valueOf(accumulatorType.apply(TO_ASM));
    builder.push(renderer);
    renderer
        .methodGen()
        .getStatic(A_RUNTIME_SUPPORT_TYPE, "USELESS_BINARY_OPERATOR", A_BINARY_OPERATOR_TYPE);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__REDUCE);
    renderer.methodGen().unbox(accumulatorType.apply(TO_ASM));

    final var output = builder.renderer(renderer.signerEmitter());
    final var argCount = output.methodGen().getArgumentTypes().length;
    accumulatorName
        .create(r -> r.methodGen().loadArg(argCount - 2))
        .forEach(v -> output.define(v.name(), v));
    name.create(r -> r.methodGen().loadArg(argCount - 1)).forEach(v -> output.define(v.name(), v));
    return output;
  }

  public Renderer renderer() {
    return renderer;
  }

  public void reverse() {
    renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_STREAM__REVERSE);
  }

  public void skip(Consumer<Renderer> limitProducer) {
    limitProducer.accept(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SKIP);
  }

  public final Renderer sort(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat targetType,
      LoadableValue... capturedVariables) {
    final var sortMethod = comparator(line, column, "Sort", name, targetType, capturedVariables);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
    return sortMethod;
  }

  public final void subsample(
      List<? extends RenderSubsampler> renderers, LoadableConstructor name) {
    final var local = renderer.methodGen().newLocal(A_SUBSAMPLER_TYPE);
    renderer.methodGen().newInstance(A_START_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().invokeConstructor(A_START_TYPE, DEFAULT_CTOR);
    renderer.methodGen().storeLocal(local);
    for (final RenderSubsampler subsample : renderers) {
      subsample.render(renderer, local, currentType, name);
    }
    renderer.methodGen().loadLocal(local);
    renderer.methodGen().swap();
    renderer.methodGen().invokeVirtual(A_SUBSAMPLER_TYPE, METHOD_SUBSAMPLER__SUBSAMPLE);
  }

  public void sum(PrimitiveStream primitive) {
    renderer
        .methodGen()
        .invokeInterface(
            primitive.outputStreamType(), new Method("sum", primitive.resultType(), new Type[0]));
  }

  public void univalued() {
    renderer.methodGen().newInstance(A_UNIVALUED_COLLECTOR_TYPE);
    renderer.methodGen().dup();
    renderer.methodGen().invokeConstructor(A_UNIVALUED_COLLECTOR_TYPE, DEFAULT_CTOR);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().unbox(A_OPTIONAL_TYPE);
  }
}
