package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Variables;
import io.prometheus.client.Gauge;

/**
 * Helper to build bytecode for “olives” (decision-action stanzas)
 */
public abstract class BaseOliveBuilder {
	private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
	protected static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
	protected static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	protected static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
	protected static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	protected static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
	protected static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	protected static final Type A_VARIABLES_TYPE = Type.getType(Variables.class);
	protected static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);

	protected static final Method METHOD_MONITOR = new Method("monitor", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_GAUGE_TYPE, A_FUNCTION_TYPE });
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
		final Method method = new Method(String.format("olive_%d_%d", oliveId, steps.size()), BOOLEAN_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(currentType))
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
					Type.getMethodType(BOOLEAN_TYPE, currentType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, currentType, RootBuilder.proxyCaptured(0, capturedVariables));
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
		final Method method = new Method(String.format("olive_%d_%d", oliveId, steps.size()), A_OBJECT_ARRAY_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(currentType))
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
					Type.getMethodType(A_OBJECT_TYPE, currentType));
			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_MONITOR);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, currentType, RootBuilder.proxyCaptured(0, capturedVariables));
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

	public RegroupVariablesBuilder smash(LoadableValue[] capturedVariables) {
		return regroup("Smash", "smash", true, capturedVariables);
	}
}
