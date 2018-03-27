package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Variables;
import io.prometheus.client.Gauge;

/**
 * Helper to build bytecode for “olives” (decision-action stanzas)
 */
public abstract class BaseOliveBuilder {
	private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
	private static final Type A_BIPREDICATE_TYPE = Type.getType(BiPredicate.class);
	protected static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
	protected static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
	protected static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	protected static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
	protected static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	protected static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
	protected static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	protected static final Type A_TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);
	protected static final Type A_VARIABLES_TYPE = Type.getType(Variables.class);
	protected static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);

	private static final Method METHOD_COMPARATOR__COMPARING = new Method("comparing", A_COMPARATOR_TYPE,
			new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_COMPARATOR__REVERSED = new Method("reversed", A_COMPARATOR_TYPE, new Type[] {});
	private static final Method METHOD_EQUALS = new Method("equals", BOOLEAN_TYPE, new Type[] { A_OBJECT_TYPE });
	private static final Method METHOD_HASH_CODE = new Method("hashCode", INT_TYPE, new Type[] {});
	protected static final Method METHOD_MONITOR = new Method("monitor", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_GAUGE_TYPE, A_FUNCTION_TYPE });

	protected static final Method METHOD_PICK = new Method("pick", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_TO_INT_FUNCTION_TYPE, A_BIPREDICATE_TYPE, A_COMPARATOR_TYPE });

	protected static final Method METHOD_REGROUP = new Method("regroup", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_FUNCTION_TYPE, A_BICONSUMER_TYPE });

	protected static final Method METHOD_STREAM__FILTER = new Method("filter", A_STREAM_TYPE,
			new Type[] { A_PREDICATE_TYPE });

	private Type currentType;

	protected final int oliveId;

	protected final RootBuilder owner;

	protected final List<Consumer<Renderer>> steps = new ArrayList<>();

	public BaseOliveBuilder(RootBuilder owner, int oliveId, Type initialType) {
		this.owner = owner;
		this.oliveId = oliveId;
		currentType = initialType;

	}

	/**
	 * Gets the current type of an olive
	 *
	 * Due to grouping clauses, the type flowing through an olive may change. This
	 * is the type at the current point in the sequence.
	 */
	protected Type currentType() {
		return currentType;
	}

	/**
	 * Create a “Where” clause in a olive.
	 *
	 * @param capturedVariables
	 *            A collection of variables that must be available in the filter
	 *            clause. These will be available in the resulting method
	 * @return a method generator for the body of the clause
	 */
	@SafeVarargs
	public final Renderer filter(LoadableValue... capturedVariables) {
		final Type type = currentType;
		final Method method = new Method(String.format("olive_%d_%d", oliveId, steps.size()), BOOLEAN_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(type))
						.toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("test",
					Type.getMethodDescriptor(A_PREDICATE_TYPE,
							Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)).toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(BOOLEAN_TYPE, type));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, type, RootBuilder.proxyCaptured(0, capturedVariables));
	}

	/**
	 * Create a “Group By” clause in a olive.
	 *
	 * @param capturedVariables
	 *            A collection of variables that must be available in the grouping
	 *            clause. These will be available in the resulting method
	 * @return a method generator for the body of the clause
	 */
	public final RegroupVariablesBuilder group(LoadableValue... capturedVariables) {
		return regroup("Group", "group", false, capturedVariables);
	}

	/**
	 * Stream of all the parameters available for capture/use in the clauses.
	 */
	public abstract Stream<LoadableValue> loadableValues();

	/**
	 * Create a “Matches” clause in an olive
	 *
	 * @param matcher
	 *            the matcher to run
	 * @param arguments
	 *            the arguments to pass as parameters to the matcher
	 */
	public final void matches(OliveDefineBuilder matcher, Stream<Consumer<Renderer>> arguments) {
		final List<Consumer<Renderer>> arglist = arguments.collect(Collectors.toList());
		if (arglist.size() != matcher.parameters()) {
			throw new IllegalArgumentException(
					String.format("Invalid number of arguments for matcher. Got %d, expected %d.", arglist.size(),
							matcher.parameters()));
		}
		if (!currentType.equals(A_VARIABLES_TYPE)) {
			throw new IllegalArgumentException("Cannot start matcher on non-initial variable type.");
		}
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			renderer.methodGen().swap();
			for (int i = 0; i < arglist.size(); i++) {
				arglist.get(i).accept(renderer);
			}
			renderer.methodGen().invokeVirtual(owner.selfType(), matcher.method());
		});
		currentType = matcher.currentType();
	}

	/**
	 * Create a “Monitor” clause in an olive
	 *
	 * @param metricName
	 *            the Prometheus metric name
	 * @param help
	 *            the help text to export
	 * @param names
	 *            the names of the labels
	 * @param capturedVariables
	 *            the variables needed in the method that computes the label values
	 * @return
	 */
	public Renderer monitor(String metricName, String help, List<String> names, LoadableValue[] capturedVariables) {
		final Type type = currentType;
		final Method method = new Method(String.format("olive_%d_%d", oliveId, steps.size()), A_OBJECT_ARRAY_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(type))
						.toArray(Type[]::new));

		steps.add(renderer -> {
			owner.loadGauge(metricName, help, names, renderer.methodGen());
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("apply",
					Type.getMethodDescriptor(A_FUNCTION_TYPE,
							Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)).toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(A_OBJECT_TYPE, type));
			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_MONITOR);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, type, RootBuilder.proxyCaptured(0, capturedVariables));
	}

	/**
	 * Select rows based on a minimum/maximum value for some expression in a group
	 *
	 * @param compareType
	 *            the type that we are finding the minimum/maximum of
	 * @param max
	 *            whether we are finding a maximum or a minimum
	 * @param discriminators
	 *            the stream variables over which the groups are constructed
	 * @param capturedVariables
	 *            any captures that are needed by the comparison expression
	 * @return a new method that must return a value to be compared for a input row
	 */
	public Renderer pick(Imyhat compareType, boolean max, Stream<Target> discriminators,
			LoadableValue[] capturedVariables) {
		final Type streamType = currentType;

		final Type[] captureTypes = Stream
				.concat(Stream.of(owner.selfType()), Arrays.stream(capturedVariables).map(LoadableValue::type))
				.toArray(Type[]::new);

		final Method hashCodeMethod = new Method(String.format("pick_%d_%d_hash", oliveId, steps.size()), INT_TYPE,
				new Type[] { streamType });

		final Method equalsMethod = new Method(String.format("pick_%d_%d_equals", oliveId, steps.size()), BOOLEAN_TYPE,
				new Type[] { streamType, streamType });

		final Method extractMethod = new Method(String.format("pick_%d_%d_ex", oliveId, steps.size()),
				compareType.boxedAsmType(),
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(streamType))
						.toArray(Type[]::new));

		steps.add(renderer -> {
			renderer.methodGen()
					.invokeDynamic("applyAsInt", Type.getMethodDescriptor(A_TO_INT_FUNCTION_TYPE),
							LAMBDA_METAFACTORY_BSM, Type.getMethodType(INT_TYPE, A_OBJECT_TYPE),
							new Handle(Opcodes.H_INVOKESTATIC, owner.selfType().getInternalName(),
									hashCodeMethod.getName(), hashCodeMethod.getDescriptor(), false),
							Type.getMethodType(INT_TYPE, streamType));

			renderer.methodGen().invokeDynamic("test", Type.getMethodDescriptor(A_BIPREDICATE_TYPE),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE),
					new Handle(Opcodes.H_INVOKESTATIC, owner.selfType().getInternalName(), equalsMethod.getName(),
							equalsMethod.getDescriptor(), false),
					Type.getMethodType(BOOLEAN_TYPE, streamType, streamType));

			renderer.methodGen().loadThis();
			for (final LoadableValue value : capturedVariables) {
				value.accept(renderer);
			}
			renderer.methodGen().invokeDynamic("apply", Type.getMethodDescriptor(A_FUNCTION_TYPE, captureTypes),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE),
					new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), extractMethod.getName(),
							extractMethod.getDescriptor(), false),
					Type.getMethodType(compareType.boxedAsmType(), streamType));
			renderer.methodGen().invokeStatic(A_COMPARATOR_TYPE, METHOD_COMPARATOR__COMPARING);
			if (max) {
				renderer.methodGen().invokeInterface(A_COMPARATOR_TYPE, METHOD_COMPARATOR__REVERSED);
			}
			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_PICK);
		});
		final GeneratorAdapter hashCodeGenerator = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				hashCodeMethod, null, null, owner.classVisitor);
		hashCodeGenerator.visitCode();
		hashCodeGenerator.push(0);

		final GeneratorAdapter equalsGenerator = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
				equalsMethod, null, null, owner.classVisitor);
		equalsGenerator.visitCode();
		final Label end = equalsGenerator.newLabel();

		discriminators.forEach(discriminator -> {
			final Method getter = new Method(discriminator.name(), discriminator.type().asmType(), new Type[] {});
			equalsGenerator.loadArg(0);
			equalsGenerator.invokeVirtual(streamType, getter);
			equalsGenerator.loadArg(1);
			equalsGenerator.invokeVirtual(streamType, getter);
			switch (discriminator.type().asmType().getSort()) {
			case Type.ARRAY:
			case Type.OBJECT:
				equalsGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
				equalsGenerator.ifZCmp(GeneratorAdapter.EQ, end);
				break;
			default:
				equalsGenerator.ifCmp(discriminator.type().asmType(), GeneratorAdapter.NE, end);
			}

			hashCodeGenerator.push(31);
			hashCodeGenerator.math(GeneratorAdapter.MUL, INT_TYPE);
			hashCodeGenerator.loadArg(0);
			hashCodeGenerator.invokeVirtual(streamType, getter);
			switch (discriminator.type().asmType().getSort()) {
			case Type.ARRAY:
			case Type.OBJECT:
				hashCodeGenerator.invokeVirtual(A_OBJECT_TYPE, METHOD_HASH_CODE);
				break;
			default:
				hashCodeGenerator.cast(discriminator.type().asmType(), INT_TYPE);
				break;
			}
			hashCodeGenerator.math(GeneratorAdapter.ADD, INT_TYPE);
		});

		hashCodeGenerator.returnValue();
		hashCodeGenerator.visitMaxs(0, 0);
		hashCodeGenerator.visitEnd();

		equalsGenerator.push(true);
		equalsGenerator.returnValue();
		equalsGenerator.mark(end);
		equalsGenerator.push(false);
		equalsGenerator.returnValue();
		equalsGenerator.visitMaxs(0, 0);
		equalsGenerator.visitEnd();

		return new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, extractMethod, null, null, owner.classVisitor),
				capturedVariables.length, streamType, RootBuilder.proxyCaptured(0, capturedVariables));

	}

	private final RegroupVariablesBuilder regroup(String classPrefix, String methodPrefix, boolean needsOk,
			LoadableValue... capturedVariables) {
		final String className = String.format("shesmu/dyn/%s%d$%d", classPrefix, oliveId, steps.size());

		final Type oldType = currentType;
		final Type newType = Type.getObjectType(className);
		currentType = newType;

		final Method newMethod = new Method(String.format("%s_%d_%d_new", methodPrefix, oliveId, steps.size()), newType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(oldType))
						.toArray(Type[]::new));
		final Method collectMethod = new Method(String.format("%s_%d_%d_collect", methodPrefix, oliveId, steps.size()),
				VOID_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(newType, oldType))
						.toArray(Type[]::new));

		steps.add(renderer -> {
			final Type[] captureTypes = Stream
					.concat(Stream.of(owner.selfType()), Arrays.stream(capturedVariables).map(LoadableValue::type))
					.toArray(Type[]::new);

			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.methodGen().invokeDynamic(
					"apply", Type.getMethodDescriptor(A_FUNCTION_TYPE, captureTypes), LAMBDA_METAFACTORY_BSM,
					Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), new Handle(Opcodes.H_INVOKEVIRTUAL,
							owner.selfType().getInternalName(), newMethod.getName(), newMethod.getDescriptor(), false),
					Type.getMethodType(newType, oldType));

			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.methodGen()
					.invokeDynamic("accept", Type.getMethodDescriptor(A_BICONSUMER_TYPE, captureTypes),
							LAMBDA_METAFACTORY_BSM, Type.getMethodType(VOID_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE),
							new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
									collectMethod.getName(), collectMethod.getDescriptor(), false),
							Type.getMethodType(VOID_TYPE, newType, oldType));

			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_REGROUP);
			if (needsOk) {
				renderer.methodGen().invokeDynamic(
						"test", Type.getMethodDescriptor(A_PREDICATE_TYPE), LAMBDA_METAFACTORY_BSM,
						Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), new Handle(Opcodes.H_INVOKEVIRTUAL, className,
								"$isOk", Type.getMethodDescriptor(BOOLEAN_TYPE), false),
						Type.getMethodType(BOOLEAN_TYPE, newType));
				renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
			}
		});

		final Renderer newMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, newMethod, null, null, owner.classVisitor),
				capturedVariables.length, oldType, RootBuilder.proxyCaptured(0, capturedVariables));
		final Renderer collectedMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, collectMethod, null, null, owner.classVisitor),
				capturedVariables.length + 1, oldType, RootBuilder.proxyCaptured(0, capturedVariables));

		return new RegroupVariablesBuilder(owner, className, newMethodGen, collectedMethodGen,
				capturedVariables.length);
	}

	/**
	 * Create a “Smash” clause
	 */
	public RegroupVariablesBuilder smash(LoadableValue[] capturedVariables) {
		return regroup("Smash", "smash", true, capturedVariables);
	}
}
