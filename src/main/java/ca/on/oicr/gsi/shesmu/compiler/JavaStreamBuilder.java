package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

/**
 * Helper to build bytecode for “olives” (decision-action stanzas)
 */
public final class JavaStreamBuilder {
	private static final Type A_BIFUNCTION_TYPE = Type.getType(BiFunction.class);
	private static final Type A_BINARY_OPERATOR_TYPE = Type.getType(BinaryOperator.class);
	private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
	private static final Type A_COLLECTORS_TYPE = Type.getType(Collectors.class);
	private static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
	private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
	private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
	private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	private static final Type A_SET_TYPE = Type.getType(Set.class);
	private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);
	private static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);

	private static final Method METHOD_COLLECTORS__TO_SET = new Method("toSet", A_COLLECTOR_TYPE, new Type[] {});
	private static final Method METHOD_OPTIONAL__OR_ELSE_GET = new Method("orElseGet", A_OBJECT_TYPE,
			new Type[] { A_SUPPLIER_TYPE });

	private static final Method METHOD_RUNTIME_SUPPORT__COMPARATOR = new Method("comparator", A_COMPARATOR_TYPE,
			new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_SET__STREAM = new Method("stream", A_STREAM_TYPE, new Type[] {});
	private static final Method METHOD_STREAM__COLLECT = new Method("collect", Type.VOID_TYPE,
			new Type[] { A_COLLECTOR_TYPE });
	private static final Method METHOD_STREAM__COUNT = new Method("count", Type.LONG_TYPE, new Type[] {});
	private static final Method METHOD_STREAM__FILTER = new Method("filter", A_STREAM_TYPE,
			new Type[] { A_PREDICATE_TYPE });
	private static final Method METHOD_STREAM__FIND_FIRST = new Method("findFirst", A_OPTIONAL_TYPE, new Type[] {});
	private static final Method METHOD_STREAM__FLAT_MAP = new Method("flatMap", A_STREAM_TYPE,
			new Type[] { A_FUNCTION_TYPE });

	private static final Method METHOD_STREAM__MAP = new Method("map", A_STREAM_TYPE, new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_STREAM__MAX = new Method("max", A_OPTIONAL_TYPE,
			new Type[] { A_COMPARATOR_TYPE });
	private static final Method METHOD_STREAM__MIN = new Method("min", A_OPTIONAL_TYPE,
			new Type[] { A_COMPARATOR_TYPE });
	private static final Method METHOD_STREAM__REDUCE = new Method("reduce", A_OBJECT_TYPE,
			new Type[] { A_OBJECT_TYPE, A_BIFUNCTION_TYPE, A_BINARY_OPERATOR_TYPE });
	private static final Method METHOD_STREAM__SORTED = new Method("sorted", A_STREAM_TYPE,
			new Type[] { A_COMPARATOR_TYPE });

	private Type currentType;
	private final RootBuilder owner;

	private final Renderer renderer;

	protected final List<Consumer<Renderer>> steps = new ArrayList<>();

	private final int streamId;

	private final Type streamType;

	public JavaStreamBuilder(RootBuilder owner, Renderer renderer, Type streamType, int streamId, Type initialType) {
		this.owner = owner;
		this.renderer = renderer;
		this.streamType = streamType;
		this.streamId = streamId;
		currentType = initialType;

	}

	public void collect() {
		finish();
		renderer.methodGen().invokeStatic(A_COLLECTORS_TYPE, METHOD_COLLECTORS__TO_SET);
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COLLECT);
		renderer.methodGen().checkCast(A_SET_TYPE);
	}

	private final Renderer comparator(String name, Imyhat targetType, LoadableValue... capturedVariables) {
		final Type sortType = currentType;

		final Method method = new Method(String.format("chain_%d_%d_compare", streamId, steps.size()),
				targetType.boxedAsmType(), Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type),
						Stream.of(streamType, sortType)).toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.loadStream();
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("apply",
					Type.getMethodDescriptor(A_FUNCTION_TYPE, Stream
							.concat(Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
							.toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(targetType.boxedAsmType(), sortType));
			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__COMPARATOR);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType, parameters(capturedVariables, name, sortType));
	}

	public void count() {
		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__COUNT);
	}

	public final Renderer filter(String name, LoadableValue... capturedVariables) {
		final Type filterType = currentType;
		final Method method = new Method(String.format("chain_%d_%d_filter", streamId, steps.size()), BOOLEAN_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type),
						Stream.of(streamType, filterType)).toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.loadStream();
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("test",
					Type.getMethodDescriptor(A_PREDICATE_TYPE, Stream
							.concat(Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
							.toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(BOOLEAN_TYPE, filterType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType, parameters(capturedVariables, name, filterType));
	}

	private final void finish() {
		renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__STREAM);

		steps.forEach(step -> step.accept(renderer));
	}

	public Renderer first(Type targetType, LoadableValue... capturedVariables) {
		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FIND_FIRST);
		return optional(targetType, capturedVariables);
	}

	public final Renderer flatten(String name, Type newType, LoadableValue... capturedVariables) {
		final Type oldType = currentType;
		currentType = newType;

		final Method method = new Method(String.format("chain_%d_%d_flatten", streamId, steps.size()), A_STREAM_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(streamType, oldType))
						.toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.loadStream();
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("apply",
					Type.getMethodDescriptor(A_FUNCTION_TYPE, Stream
							.concat(Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
							.toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(newType, oldType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FLAT_MAP);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType, parameters(capturedVariables, name, oldType));
	}

	public final Renderer map(String name, Type newType, LoadableValue... capturedVariables) {
		final Type oldType = currentType;
		currentType = newType;

		final Method method = new Method(String.format("chain_%d_%d_map", streamId, steps.size()), newType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(streamType, oldType))
						.toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.loadStream();
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("apply",
					Type.getMethodDescriptor(A_FUNCTION_TYPE, Stream
							.concat(Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
							.toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(newType, oldType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, streamType, parameters(capturedVariables, name, oldType));
	}

	public Pair<Renderer, Renderer> optima(boolean max, String name, Imyhat targetType,
			LoadableValue... capturedVariables) {
		final Renderer extractRenderer = comparator(name, targetType, capturedVariables);

		finish();
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, max ? METHOD_STREAM__MAX : METHOD_STREAM__MIN);

		return new Pair<>(extractRenderer, optional(currentType, capturedVariables));
	}

	private Renderer optional(Type targetType, LoadableValue... capturedVariables) {

		final Method defaultMethod = new Method(String.format("chain_%d_default", streamId), targetType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(streamType))
						.toArray(Type[]::new));

		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
				defaultMethod.getName(), defaultMethod.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("get",
				Type.getMethodDescriptor(A_SUPPLIER_TYPE, Stream
						.concat(Stream.concat(Stream.of(owner.selfType()),
								Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
						.toArray(Type[]::new)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE), handle, Type.getMethodType(targetType));
		renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OR_ELSE_GET);
		renderer.methodGen().unbox(targetType);

		return new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, defaultMethod, null, null, owner.classVisitor),
				capturedVariables.length, streamType, RootBuilder.proxyCaptured(0, capturedVariables));
	}

	private Stream<LoadableValue> parameters(LoadableValue[] capturedVariables, String name, Type type) {
		final int index = capturedVariables.length + 1;
		return Stream.concat(RootBuilder.proxyCaptured(0, capturedVariables), Stream.of(new LoadableValue() {

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

	public Renderer reduce(String name, Type accumulatorType, String accumulatorName, Consumer<Renderer> initial,
			LoadableValue... capturedVariables) {

		final Method defaultMethod = new Method(String.format("chain_%d_reduce", streamId), accumulatorType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type),
						Stream.of(streamType, accumulatorType, currentType)).toArray(Type[]::new));

		finish();
		initial.accept(renderer);
		renderer.methodGen().box(accumulatorType);
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
				defaultMethod.getName(), defaultMethod.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("apply",
				Type.getMethodDescriptor(A_BIFUNCTION_TYPE, Stream
						.concat(Stream.concat(Stream.of(owner.selfType()),
								Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
						.toArray(Type[]::new)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(accumulatorType, accumulatorType, currentType));
		renderer.methodGen().getStatic(A_RUNTIME_SUPPORT_TYPE, "USELESS_BINARY_OPERATOR", A_BINARY_OPERATOR_TYPE);
		renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__REDUCE);
		renderer.methodGen().unbox(accumulatorType);

		return new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, defaultMethod, null, null, owner.classVisitor),
				capturedVariables.length, streamType,
				Stream.concat(RootBuilder.proxyCaptured(0, capturedVariables), Stream.of(new LoadableValue() {

					@Override
					public void accept(Renderer renderer) {
						renderer.methodGen().loadArg(capturedVariables.length + 1);
					}

					@Override
					public String name() {
						return accumulatorName;
					}

					@Override
					public Type type() {
						return accumulatorType;
					}
				}, new LoadableValue() {

					@Override
					public void accept(Renderer renderer) {
						renderer.methodGen().loadArg(capturedVariables.length + 2);
					}

					@Override
					public String name() {
						return name;
					}

					@Override
					public Type type() {
						return currentType;
					}
				})));

	}

	public Renderer renderer() {
		return renderer;
	}

	public final Renderer sort(String name, Imyhat targetType, LoadableValue... capturedVariables) {
		final Renderer sortMethod = comparator(name, targetType, capturedVariables);
		steps.add(renderer -> {
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
		});
		return sortMethod;
	}
}
