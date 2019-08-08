package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.runtime.UnivaluedCollector;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Start;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Subsampler;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
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
  private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
  private static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_UNIVALUED_COLLECTOR_TYPE = Type.getType(UnivaluedCollector.class);
  private static final Type A_START_TYPE = Type.getType(Start.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Type A_SUBSAMPLER_TYPE = Type.getType(Subsampler.class);
  private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);

  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

  private static final Method METHOD_COLLECTORS__TO_SET =
      new Method("toSet", A_COLLECTOR_TYPE, new Type[] {});
  private static final Method METHOD_COMPARATOR__COMPARING =
      new Method("comparing", A_COMPARATOR_TYPE, new Type[] {A_FUNCTION_TYPE});

  private static final Method METHOD_OPTIONAL__OR_ELSE_GET =
      new Method("orElseGet", A_OBJECT_TYPE, new Type[] {A_SUPPLIER_TYPE});
  private static final Method METHOD_UNIVALUED_COLLECTOR__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_SUPPLIER_TYPE});
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

  private Imyhat currentType;

  private final RootBuilder owner;

  private final Renderer renderer;

  private final Type streamType;

  public JavaStreamBuilder(
      RootBuilder owner, Renderer renderer, Type streamType, Imyhat initialType) {
    this.owner = owner;
    this.renderer = renderer;
    this.streamType = streamType;
    currentType = initialType;
  }

  public void collect() {
    finish();
    renderer.loadImyhat(currentType.descriptor());
    renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_COLLECTORS__TO_SET);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().checkCast(A_SET_TYPE);
  }

  public void collector(Type resultType, Consumer<Renderer> loadCollector) {
    finish();
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
    final LambdaBuilder builder =
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
    finish();
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COUNT);
  }

  public void distinct() {
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__DISTINCT);
  }

  public final Renderer filter(
      int line, int column, LoadableConstructor name, LoadableValue... capturedVariables) {
    final LambdaBuilder builder =
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

  public final void finish() {}

  public Renderer first(
      int line, int column, LoadableConstructor name, LoadableValue... capturedVariables) {
    finish();
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FIND_FIRST);
    return optional(line, column, "First", currentType, name, capturedVariables);
  }

  public final Renderer flatten(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat newType,
      LoadableValue... capturedVariables) {
    final Imyhat oldType = currentType;
    currentType = newType;

    final LambdaBuilder builder =
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

  private Renderer makeRender(LambdaBuilder builder, LoadableConstructor name) {
    final Renderer output = builder.renderer(renderer.signerEmitter());
    final int argCount = output.methodGen().getArgumentTypes().length;
    name.create(r -> r.methodGen().loadArg(argCount - 1)).forEach(v -> output.define(v.name(), v));
    return output;
  }

  public void limit(Consumer<Renderer> limitProducer) {
    limitProducer.accept(renderer);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__LIMIT);
  }

  public final Renderer map(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat newType,
      LoadableValue... capturedVariables) {
    final Imyhat oldType = currentType;
    currentType = newType;

    final LambdaBuilder builder =
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

  public final Renderer match(
      int line,
      int column,
      Match matchType,
      LoadableConstructor name,
      LoadableValue... capturedVariables) {
    finish();
    final LambdaBuilder builder =
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

  public Pair<Renderer, Renderer> optima(
      int line,
      int column,
      boolean max,
      LoadableConstructor name,
      Imyhat targetType,
      LoadableValue... capturedVariables) {
    final Renderer extractRenderer =
        comparator(line, column, max ? "Max" : "Min", name, targetType, capturedVariables);

    finish();
    renderer
        .methodGen()
        .invokeInterface(A_STREAM_TYPE, max ? METHOD_STREAM__MAX : METHOD_STREAM__MIN);

    return new Pair<>(
        extractRenderer,
        optional(line, column, max ? "Max" : "Min", currentType, name, capturedVariables));
  }

  private Renderer optional(
      int line,
      int column,
      String syntax,
      Imyhat targetType,
      LoadableConstructor name,
      LoadableValue... capturedVariables) {

    final LambdaBuilder builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ %s Default %d:%d", syntax, line, column),
            LambdaBuilder.supplier(targetType),
            renderer.streamType(),
            capturedVariables);
    builder.push(renderer);
    renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OR_ELSE_GET);
    renderer.methodGen().unbox(targetType.apply(TO_ASM));

    return makeRender(builder, name);
  }

  public Renderer reduce(
      int line,
      int column,
      LoadableConstructor name,
      Imyhat accumulatorType,
      LoadableConstructor accumulatorName,
      Consumer<Renderer> initial,
      LoadableValue... capturedVariables) {

    final LambdaBuilder builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Reduce %d:%d", line, column),
            LambdaBuilder.bifunction(accumulatorType, accumulatorType, currentType),
            renderer.streamType(),
            capturedVariables);
    finish();
    initial.accept(renderer);
    renderer.methodGen().valueOf(accumulatorType.apply(TO_ASM));
    builder.push(renderer);
    renderer
        .methodGen()
        .getStatic(A_RUNTIME_SUPPORT_TYPE, "USELESS_BINARY_OPERATOR", A_BINARY_OPERATOR_TYPE);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__REDUCE);
    renderer.methodGen().unbox(accumulatorType.apply(TO_ASM));

    final Renderer output = builder.renderer(renderer.signerEmitter());
    final int argCount = output.methodGen().getArgumentTypes().length;
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

  public Renderer univalued(
      int line, int column, LoadableConstructor name, LoadableValue... capturedVariables) {
    finish();
    final LambdaBuilder builder =
        new LambdaBuilder(
            owner,
            String.format("For ⋯ Univalued Default %d:%d", line, column),
            LambdaBuilder.supplier(currentType),
            renderer.streamType(),
            capturedVariables);

    renderer.methodGen().newInstance(A_UNIVALUED_COLLECTOR_TYPE);
    renderer.methodGen().dup();
    builder.push(renderer);
    renderer
        .methodGen()
        .invokeConstructor(A_UNIVALUED_COLLECTOR_TYPE, METHOD_UNIVALUED_COLLECTOR__CTOR);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
    renderer.methodGen().unbox(currentType.apply(TO_ASM));
    return makeRender(builder, name);
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
    final Renderer sortMethod =
        comparator(line, column, "Sort", name, targetType, capturedVariables);
    renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
    return sortMethod;
  }

  public final void subsample(
      List<? extends RenderSubsampler> renderers, LoadableConstructor name) {
    final int local = renderer.methodGen().newLocal(A_SUBSAMPLER_TYPE);
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
}
