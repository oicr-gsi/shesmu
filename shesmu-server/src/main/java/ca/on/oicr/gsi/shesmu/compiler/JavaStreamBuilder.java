package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.Arrays;
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

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.subsample.Start;
import ca.on.oicr.gsi.shesmu.subsample.Subsampler;

/**
 * Helper to build bytecode for “olives” (decision-action stanzas)
 */
public final class JavaStreamBuilder {
	public interface RenderSubsampler {
		void render(Renderer renderer, int previousLocal, String prefix, int index, Type streamType);
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
	private static final Type A_START_TYPE = Type.getType(Start.class);
	private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	private static final Type A_SUBSAMPLER_TYPE = Type.getType(Subsampler.class);
	private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
	private static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);
	private static final Method METHOD_COLLECTORS__TO_SET = new Method("toSet", A_COLLECTOR_TYPE, new Type[] {});
	private static final Method METHOD_COMPARATOR__COMPARING = new Method("comparing", A_COMPARATOR_TYPE,
			new Type[] { A_FUNCTION_TYPE });

	private static final Method METHOD_OPTIONAL__OR_ELSE_GET = new Method("orElseGet", A_OBJECT_TYPE,
			new Type[] { A_SUPPLIER_TYPE });
	private static final Method METHOD_STREAM__COLLECT = new Method("collect", A_OBJECT_TYPE,
			new Type[] { A_COLLECTOR_TYPE });
	private static final Method METHOD_STREAM__COUNT = new Method("count", Type.LONG_TYPE, new Type[] {});
	private static final Method METHOD_STREAM__DISTINCT = new Method("distinct", A_STREAM_TYPE, new Type[] {});
	private static final Method METHOD_STREAM__FILTER = new Method("filter", A_STREAM_TYPE,
			new Type[] { A_PREDICATE_TYPE });
	private static final Method METHOD_STREAM__FIND_FIRST = new Method("findFirst", A_OPTIONAL_TYPE, new Type[] {});

	private static final Method METHOD_STREAM__FLAT_MAP = new Method("flatMap", A_STREAM_TYPE,
			new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_STREAM__LIMIT = new Method("limit", A_STREAM_TYPE,
			new Type[] { Type.LONG_TYPE });
	private static final Method METHOD_STREAM__MAP = new Method("map", A_STREAM_TYPE, new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_STREAM__MAX = new Method("max", A_OPTIONAL_TYPE,
			new Type[] { A_COMPARATOR_TYPE });
	private static final Method METHOD_STREAM__MIN = new Method("min", A_OPTIONAL_TYPE,
			new Type[] { A_COMPARATOR_TYPE });
	private static final Method METHOD_STREAM__REDUCE = new Method("reduce", A_OBJECT_TYPE,
			new Type[] { A_OBJECT_TYPE, A_BIFUNCTION_TYPE, A_BINARY_OPERATOR_TYPE });
	private static final Method METHOD_STREAM__REVERSE = new Method("reverse", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE });
	private static final Method METHOD_STREAM__SKIP = new Method("skip", A_STREAM_TYPE, new Type[] { Type.LONG_TYPE });
	private static final Method METHOD_STREAM__SORTED = new Method("sorted", A_STREAM_TYPE,
			new Type[] { A_COMPARATOR_TYPE });

	private static final Method METHOD_SUBSAMPLER__SUBSAMPLE = new Method("subsample", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE });

	public static Stream<LoadableValue> parameters(LoadableValue[] capturedVariables, Type streamType, String name,
			Type type) {
		final int index = capturedVariables.length + (streamType == null ? 0 : 1);
		return Stream.concat(//
				RootBuilder.proxyCaptured(0, capturedVariables), //
				Stream.of(new LoadableValue() {

					@Override
					public void accept(Renderer renderer) {
						renderer.methodGen().loadArg(index);
					}

					@Override
					public String name() {
						return name;
					}

					@Override
					public Type type() {
						return type;
					}
				}));
	}

	public static Type[] parameterTypes(RootBuilder owner, boolean includeSelf, LoadableValue[] capturedVariables,
			Type streamType, Imyhat... newParameters) {
		return Stream.<Supplier<Stream<Type>>>of(() -> includeSelf ? Stream.of(owner.selfType()) : Stream.empty(), //
				() -> Stream.of(capturedVariables).map(LoadableValue::type), //
				() -> streamType == null ? Stream.empty() : Stream.of(streamType), //
				() -> Stream.of(newParameters).map(Imyhat::asmType))//
				.flatMap(Supplier::get).toArray(Type[]::new);
	}

	private Imyhat currentType;

	private final RootBuilder owner;

	private final Renderer renderer;

	int steps;

	private final int streamId;

	private final Type streamType;

	public JavaStreamBuilder(RootBuilder owner, Renderer renderer, Type streamType, int streamId, Imyhat initialType) {
		this.owner = owner;
		this.renderer = renderer;
		this.streamType = streamType;
		this.streamId = streamId;
		currentType = initialType;

	}

	public void collect() {
		finish();
		renderer.loadImyhat(currentType.signature());
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

	private final Renderer comparator(String name, Imyhat targetType, LoadableValue... capturedVariables) {
		final Imyhat sortType = currentType;

		final Method method = new Method(String.format("chain_%d_%d_compare", streamId, steps++),
				targetType.boxedAsmType(), parameterTypes(owner, false, capturedVariables, streamType, sortType));
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("apply",
				Type.getMethodDescriptor(A_FUNCTION_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(targetType.boxedAsmType(), sortType.boxedAsmType()));
		renderer.invokeInterfaceStatic(A_COMPARATOR_TYPE, METHOD_COMPARATOR__COMPARING);
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				parameters(capturedVariables, streamType, name, sortType.asmType()), renderer.signerEmitter());
	}

	public void count() {
		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COUNT);
	}

	public void distinct() {
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__DISTINCT);
	}

	public final Renderer filter(String name, LoadableValue... capturedVariables) {
		final Imyhat filterType = currentType;
		final Method method = new Method(String.format("chain_%d_%d_filter", streamId, steps++), BOOLEAN_TYPE,
				parameterTypes(owner, false, capturedVariables, streamType, filterType));
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("test",
				Type.getMethodDescriptor(A_PREDICATE_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(BOOLEAN_TYPE, filterType.boxedAsmType()));
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				parameters(capturedVariables, streamType, name, filterType.asmType()), renderer.signerEmitter());
	}

	public final void finish() {
	}

	public Renderer first(Imyhat targetType, LoadableValue... capturedVariables) {
		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FIND_FIRST);
		return optional(targetType, capturedVariables);
	}

	public final Renderer flatten(String name, Imyhat newType, LoadableValue... capturedVariables) {
		final Imyhat oldType = currentType;
		currentType = newType;

		final Method method = new Method(String.format("chain_%d_%d_flatten", streamId, steps++), A_STREAM_TYPE,
				parameterTypes(owner, false, capturedVariables, streamType, oldType));
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("apply",
				Type.getMethodDescriptor(A_FUNCTION_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(A_STREAM_TYPE, oldType.boxedAsmType()));
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FLAT_MAP);
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				parameters(capturedVariables, streamType, name, oldType.asmType()), renderer.signerEmitter());
	}

	public void limit(Consumer<Renderer> limitProducer) {
		limitProducer.accept(renderer);
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__LIMIT);
	}

	public final Renderer map(String name, Imyhat newType, LoadableValue... capturedVariables) {
		final Imyhat oldType = currentType;
		currentType = newType;

		final Method method = new Method(String.format("chain_%d_%d_map", streamId, steps++), newType.asmType(),
				parameterTypes(owner, false, capturedVariables, streamType, oldType));
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("apply",
				Type.getMethodDescriptor(A_FUNCTION_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(newType.boxedAsmType(), oldType.boxedAsmType()));
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				parameters(capturedVariables, streamType, name, oldType.asmType()), renderer.signerEmitter());
	}

	public final Renderer match(Match matchType, String name, LoadableValue... capturedVariables) {
		finish();
		final Method method = new Method(String.format("chain_%d_match", streamId), BOOLEAN_TYPE,
				parameterTypes(owner, false, capturedVariables, streamType, currentType));
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
				method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("test",
				Type.getMethodDescriptor(A_PREDICATE_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(BOOLEAN_TYPE, currentType.boxedAsmType()));
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, matchType.method);
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				parameters(capturedVariables, streamType, name, currentType.asmType()), renderer.signerEmitter());
	}

	public Pair<Renderer, Renderer> optima(boolean max, String name, Imyhat targetType,
			LoadableValue... capturedVariables) {
		final Renderer extractRenderer = comparator(name, targetType, capturedVariables);

		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, max ? METHOD_STREAM__MAX : METHOD_STREAM__MIN);

		return new Pair<>(extractRenderer, optional(currentType, capturedVariables));
	}

	private Renderer optional(Imyhat targetType, LoadableValue... capturedVariables) {

		final Method defaultMethod = new Method(String.format("chain_%d_default", streamId), targetType.asmType(),
				parameterTypes(owner, false, capturedVariables, streamType));

		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
				defaultMethod.getName(), defaultMethod.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("get",
				Type.getMethodDescriptor(A_SUPPLIER_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE), handle,
				Type.getMethodType(targetType.boxedAsmType()));
		renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OR_ELSE_GET);
		renderer.methodGen().unbox(targetType.asmType());

		return new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, defaultMethod, null, null, owner.classVisitor),
				capturedVariables.length, streamType, RootBuilder.proxyCaptured(0, capturedVariables),
				renderer.signerEmitter());
	}

	public Renderer reduce(String name, Imyhat accumulatorType, String accumulatorName, Consumer<Renderer> initial,
			LoadableValue... capturedVariables) {

		final Method defaultMethod = new Method(String.format("chain_%d_reduce", streamId), accumulatorType.asmType(),
				parameterTypes(owner, false, capturedVariables, streamType, accumulatorType, currentType));

		finish();
		initial.accept(renderer);
		renderer.methodGen().box(accumulatorType.asmType());
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
				defaultMethod.getName(), defaultMethod.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("apply",
				Type.getMethodDescriptor(A_BIFUNCTION_TYPE, parameterTypes(owner, true, capturedVariables, streamType)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(accumulatorType.boxedAsmType(), accumulatorType.boxedAsmType(),
						currentType.boxedAsmType()));
		renderer.methodGen().getStatic(A_RUNTIME_SUPPORT_TYPE, "USELESS_BINARY_OPERATOR", A_BINARY_OPERATOR_TYPE);
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__REDUCE);
		renderer.methodGen().unbox(accumulatorType.asmType());

		return new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, defaultMethod, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				Stream.concat(RootBuilder.proxyCaptured(0, capturedVariables), Stream.of(new LoadableValue() {

					@Override
					public void accept(Renderer renderer) {
						renderer.methodGen().loadArg(capturedVariables.length + (streamType == null ? 0 : 1));
					}

					@Override
					public String name() {
						return accumulatorName;
					}

					@Override
					public Type type() {
						return accumulatorType.asmType();
					}
				}, new LoadableValue() {

					@Override
					public void accept(Renderer renderer) {
						renderer.methodGen().loadArg(capturedVariables.length + (streamType == null ? 1 : 2));
					}

					@Override
					public String name() {
						return name;
					}

					@Override
					public Type type() {
						return currentType.asmType();
					}
				})), renderer.signerEmitter());

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

	public final Renderer sort(String name, Imyhat targetType, LoadableValue... capturedVariables) {
		final Renderer sortMethod = comparator(name, targetType, capturedVariables);
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
		return sortMethod;
	}

	public final void subsample(List<RenderSubsampler> renderers) {
		final String prefix = String.format("chain_%d_%d_", streamId, steps++);
		final int local = renderer.methodGen().newLocal(A_SUBSAMPLER_TYPE);
		renderer.methodGen().newInstance(A_START_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().invokeConstructor(A_START_TYPE, DEFAULT_CTOR);
		renderer.methodGen().storeLocal(local);
		int index = 0;
		for (final RenderSubsampler subsample : renderers) {
			subsample.render(renderer, local, prefix, index++, renderer.streamType());
		}
		renderer.methodGen().loadLocal(local);
		renderer.methodGen().swap();
		renderer.methodGen().invokeVirtual(A_SUBSAMPLER_TYPE, METHOD_SUBSAMPLER__SUBSAMPLE);
	}
}
