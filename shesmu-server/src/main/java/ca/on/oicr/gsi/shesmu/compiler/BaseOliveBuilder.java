package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
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

import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.SignatureVariable;
import io.prometheus.client.Gauge;

/**
 * Helper to build bytecode for “olives” (decision-action stanzas)
 */
public abstract class BaseOliveBuilder {
	protected static final Type A_ACTION_GENERATOR_TYPE = Type.getType(ActionGenerator.class);
	private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
	private static final Type A_BIFUNCTION_TYPE = Type.getType(BiFunction.class);
	private static final Type A_BIPREDICATE_TYPE = Type.getType(BiPredicate.class);
	protected static final Type A_COMPARATOR_TYPE = Type.getType(Comparator.class);
	protected static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
	protected static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
	protected static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	protected static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	protected static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
	protected static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	protected static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
	protected static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	protected static final Type A_SET_TYPE = Type.getType(Set.class);
	protected static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	protected static final Type A_STRING_TYPE = Type.getType(String.class);
	protected static final Type A_TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);

	protected static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);
	private static final Method METHOD_ACTION_GENERATOR__MEASURE_FLOW = new Method("measureFlow", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_STRING_TYPE, INT_TYPE, INT_TYPE });
	private static final Method METHOD_COMPARATOR__COMPARING = new Method("comparing", A_COMPARATOR_TYPE,
			new Type[] { A_FUNCTION_TYPE });
	private static final Method METHOD_COMPARATOR__REVERSED = new Method("reversed", A_COMPARATOR_TYPE, new Type[] {});
	private static final Method METHOD_EQUALS = new Method("equals", BOOLEAN_TYPE, new Type[] { A_OBJECT_TYPE });

	protected static final Method METHOD_FUNCTION__APPLY = new Method("apply", A_OBJECT_TYPE,
			new Type[] { A_OBJECT_TYPE });

	private static final Method METHOD_HASH_CODE = new Method("hashCode", INT_TYPE, new Type[] {});

	protected static final Method METHOD_LEFT_JOIN = new Method("leftJoin", A_STREAM_TYPE, new Type[] { A_STREAM_TYPE,
			Type.getType(Class.class), A_FUNCTION_TYPE, A_BIFUNCTION_TYPE, A_FUNCTION_TYPE, A_BICONSUMER_TYPE });
	protected static final Method METHOD_MONITOR = new Method("monitor", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_GAUGE_TYPE, A_FUNCTION_TYPE });
	protected static final Method METHOD_PICK = new Method("pick", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_TO_INT_FUNCTION_TYPE, A_BIPREDICATE_TYPE, A_COMPARATOR_TYPE });
	protected static final Method METHOD_REGROUP = new Method("regroup", A_STREAM_TYPE,
			new Type[] { A_STREAM_TYPE, A_FUNCTION_TYPE, A_BICONSUMER_TYPE });
	protected static final Method METHOD_STREAM__FILTER = new Method("filter", A_STREAM_TYPE,
			new Type[] { A_PREDICATE_TYPE });
	protected static final Method METHOD_STREAM__FLAT_MAP = new Method("flatMap", A_STREAM_TYPE,
			new Type[] { A_FUNCTION_TYPE });
	protected static final Method METHOD_STREAM__MAP = new Method("map", A_STREAM_TYPE, new Type[] { A_FUNCTION_TYPE });
	protected static final Method METHOD_STREAM__PEEK = new Method("peek", A_STREAM_TYPE,
			new Type[] { A_CONSUMER_TYPE });

	private Type currentType;

	protected final Type initialType;

	protected final int oliveId;

	protected final RootBuilder owner;
	protected final List<Consumer<Renderer>> steps = new ArrayList<>();

	public BaseOliveBuilder(RootBuilder owner, int oliveId, Type initialType) {
		this.owner = owner;
		this.oliveId = oliveId;
		this.initialType = initialType;
		currentType = initialType;

	}

	/**
	 * Create a “Matches” clause in an olive
	 *
	 * @param matcher
	 *            the matcher to run
	 * @param arguments
	 *            the arguments to pass as parameters to the matcher
	 */
	public final void call(OliveDefineBuilder matcher, Stream<Consumer<Renderer>> arguments) {
		final List<Consumer<Renderer>> arglist = arguments.collect(Collectors.toList());
		if (arglist.size() != matcher.parameters()) {
			throw new IllegalArgumentException(
					String.format("Invalid number of arguments for matcher. Got %d, expected %d.", arglist.size(),
							matcher.parameters()));
		}
		if (!currentType.equals(owner.inputFormatDefinition().type())) {
			throw new IllegalArgumentException("Cannot start matcher on non-initial variable type.");
		}
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			renderer.methodGen().swap();
			renderer.methodGen().loadArg(1);
			NameDefinitions.signatureVariables().forEach(signer -> loadSigner(signer, renderer));
			for (int i = 0; i < arglist.size(); i++) {
				arglist.get(i).accept(renderer);
			}
			renderer.methodGen().invokeVirtual(owner.selfType(), matcher.method());
		});
		currentType = matcher.currentType();
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

	protected abstract void emitSigner(SignatureVariable name, Renderer renderer);

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
				capturedVariables.length, type, RootBuilder.proxyCaptured(0, capturedVariables), this::emitSigner);
	}

	public final JoinBuilder join(Type innerType) {
		final String className = String.format("shesmu/dyn/Join%d$%d", oliveId, steps.size());

		final Type oldType = currentType;
		final Type newType = Type.getObjectType(className);
		currentType = newType;

		final Method flatMapMethod = new Method(String.format("join_%d_%d", oliveId, steps.size()), A_STREAM_TYPE,
				new Type[] { A_FUNCTION_TYPE, oldType });

		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			renderer.methodGen().loadArg(1);
			renderer.methodGen()
					.invokeDynamic("apply",
							Type.getMethodDescriptor(A_FUNCTION_TYPE, new Type[] { owner.selfType(), A_FUNCTION_TYPE }),
							LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE),
							new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
									flatMapMethod.getName(), flatMapMethod.getDescriptor(), false),
							Type.getMethodType(A_STREAM_TYPE, oldType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FLAT_MAP);
		});

		final GeneratorAdapter flatMapMethodGen = new GeneratorAdapter(Opcodes.ACC_PUBLIC, flatMapMethod, null, null,
				owner.classVisitor);
		flatMapMethodGen.visitCode();
		flatMapMethodGen.loadArg(0);
		flatMapMethodGen.push(innerType);
		flatMapMethodGen.invokeInterface(A_FUNCTION_TYPE, METHOD_FUNCTION__APPLY);
		flatMapMethodGen.checkCast(A_STREAM_TYPE);

		flatMapMethodGen.loadArg(1);
		flatMapMethodGen.invokeDynamic("apply", Type.getMethodDescriptor(A_FUNCTION_TYPE, new Type[] { oldType }),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE),
				new Handle(Opcodes.H_NEWINVOKESPECIAL, newType.getInternalName(), "<init>",
						Type.getMethodDescriptor(Type.VOID_TYPE, oldType, innerType), false),
				Type.getMethodType(newType, innerType));
		flatMapMethodGen.invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
		flatMapMethodGen.returnValue();
		flatMapMethodGen.visitMaxs(0, 0);
		flatMapMethodGen.visitEnd();

		return new JoinBuilder(owner, newType, oldType, innerType);
	}

	public final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin(Type innerType,
			LoadableValue... capturedVariables) {
		final String joinedClassName = String.format("shesmu/dyn/LeftJoinTemporary%d$%d", oliveId, steps.size());
		final String outputClassName = String.format("shesmu/dyn/LeftJoin%d$%d", oliveId, steps.size());

		final Type oldType = currentType;
		final Type joinedType = Type.getObjectType(joinedClassName);
		final Type newType = Type.getObjectType(outputClassName);
		currentType = newType;

		final Method newMethod = new Method(String.format("leftjoin_%d_%d_new", oliveId, steps.size()), newType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(joinedType))
						.toArray(Type[]::new));
		final Method collectMethod = new Method(String.format("leftjoin_%d_%d_collect", oliveId, steps.size()),
				VOID_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(newType, joinedType))
						.toArray(Type[]::new));

		final Type[] captureTypes = Stream
				.concat(Stream.of(owner.selfType()), Arrays.stream(capturedVariables).map(LoadableValue::type))
				.toArray(Type[]::new);

		steps.add(renderer -> {
			renderer.methodGen().push(innerType);
			renderer.methodGen().loadArg(1);

			renderer.methodGen().invokeDynamic("apply", Type.getMethodDescriptor(A_BIFUNCTION_TYPE, new Type[] {}),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE),
					new Handle(Opcodes.H_NEWINVOKESPECIAL, joinedType.getInternalName(), "<init>",
							Type.getMethodDescriptor(Type.VOID_TYPE, oldType, innerType), false),
					Type.getMethodType(joinedType, oldType, innerType));

			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.methodGen().invokeDynamic(
					"apply", Type.getMethodDescriptor(A_FUNCTION_TYPE, captureTypes), LAMBDA_METAFACTORY_BSM,
					Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE), new Handle(Opcodes.H_INVOKEVIRTUAL,
							owner.selfType().getInternalName(), newMethod.getName(), newMethod.getDescriptor(), false),
					Type.getMethodType(newType, joinedType));

			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.methodGen()
					.invokeDynamic("accept", Type.getMethodDescriptor(A_BICONSUMER_TYPE, captureTypes),
							LAMBDA_METAFACTORY_BSM, Type.getMethodType(VOID_TYPE, A_OBJECT_TYPE, A_OBJECT_TYPE),
							new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
									collectMethod.getName(), collectMethod.getDescriptor(), false),
							Type.getMethodType(VOID_TYPE, newType, joinedType));

			renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_LEFT_JOIN);

			renderer.methodGen().invokeDynamic(
					"test", Type.getMethodDescriptor(A_PREDICATE_TYPE), LAMBDA_METAFACTORY_BSM,
					Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), new Handle(Opcodes.H_INVOKEVIRTUAL,
							outputClassName, "$isOk", Type.getMethodDescriptor(BOOLEAN_TYPE), false),
					Type.getMethodType(BOOLEAN_TYPE, newType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);

		});

		final Renderer newMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, newMethod, null, null, owner.classVisitor),
				capturedVariables.length, joinedType, RootBuilder.proxyCaptured(0, capturedVariables),
				this::emitSigner);
		final Renderer collectedMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, collectMethod, null, null, owner.classVisitor),
				capturedVariables.length + 1, joinedType, RootBuilder.proxyCaptured(0, capturedVariables),
				this::emitSigner);

		return new Pair<>(new JoinBuilder(owner, joinedType, oldType, innerType), new RegroupVariablesBuilder(owner,
				outputClassName, newMethodGen, collectedMethodGen, capturedVariables.length));
	}

	public final LetBuilder let(LoadableValue... capturedVariables) {
		final String className = String.format("shesmu/dyn/Let%d$%d", oliveId, steps.size());

		final Type oldType = currentType;
		final Type newType = Type.getObjectType(className);
		currentType = newType;

		final Method createMethod = new Method(String.format("let_%d_%d", oliveId, steps.size()), newType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(oldType))
						.toArray(Type[]::new));

		steps.add(renderer -> {
			final Type[] captureTypes = Stream
					.concat(Stream.of(owner.selfType()), Arrays.stream(capturedVariables).map(LoadableValue::type))
					.toArray(Type[]::new);

			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			renderer.methodGen()
					.invokeDynamic("apply", Type.getMethodDescriptor(A_FUNCTION_TYPE, captureTypes),
							LAMBDA_METAFACTORY_BSM, Type.getMethodType(A_OBJECT_TYPE, A_OBJECT_TYPE),
							new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
									createMethod.getName(), createMethod.getDescriptor(), false),
							Type.getMethodType(newType, oldType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
		});

		final Renderer createMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, createMethod, null, null, owner.classVisitor),
				capturedVariables.length, oldType, RootBuilder.proxyCaptured(0, capturedVariables), this::emitSigner);

		return new LetBuilder(owner, newType, createMethodGen);
	}

	public void line(int line) {
		steps.add(renderer -> renderer.mark(line));
	}

	/**
	 * Stream of all the parameters available for capture/use in the clauses.
	 */
	public abstract Stream<LoadableValue> loadableValues();

	protected abstract void loadSigner(SignatureVariable variable, Renderer renderer);

	/**
	 * Measure how much data goes through this olive clause.
	 */
	public final void measureFlow(String filename, int line, int column) {
		steps.add(renderer -> {
			renderer.methodGen().push(filename);
			renderer.methodGen().push(line);
			renderer.methodGen().push(column);
			renderer.methodGen().invokeStatic(A_ACTION_GENERATOR_TYPE, METHOD_ACTION_GENERATOR__MEASURE_FLOW);
		});

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
				capturedVariables.length, type, RootBuilder.proxyCaptured(0, capturedVariables), this::emitSigner);
	}

	public Renderer peek(LoadableValue[] capturedVariables) {
		final Type type = currentType;
		final Method method = new Method(String.format("olive_%d_%d", oliveId, steps.size()), VOID_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(type))
						.toArray(Type[]::new));
		steps.add(renderer -> {
			renderer.methodGen().loadThis();
			Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
			final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(),
					method.getName(), method.getDescriptor(), false);
			renderer.methodGen().invokeDynamic("accept",
					Type.getMethodDescriptor(A_CONSUMER_TYPE,
							Stream.concat(Stream.of(owner.selfType()),
									Arrays.stream(capturedVariables).map(LoadableValue::type)).toArray(Type[]::new)),
					LAMBDA_METAFACTORY_BSM, Type.getMethodType(VOID_TYPE, A_OBJECT_TYPE), handle,
					Type.getMethodType(VOID_TYPE, type));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__PEEK);
		});
		return new Renderer(owner, new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor),
				capturedVariables.length, type, RootBuilder.proxyCaptured(0, capturedVariables), this::emitSigner);
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
			renderer.invokeInterfaceStatic(A_COMPARATOR_TYPE, METHOD_COMPARATOR__COMPARING);
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
				hashCodeGenerator.invokeStatic(discriminator.type().boxedAsmType(),
						new Method("hashCode", INT_TYPE, new Type[] { discriminator.type().asmType() }));
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
				capturedVariables.length, streamType, RootBuilder.proxyCaptured(0, capturedVariables),
				this::emitSigner);

	}

	public final RegroupVariablesBuilder regroup(LoadableValue... capturedVariables) {
		final String className = String.format("shesmu/dyn/Group%d$%d", oliveId, steps.size());

		final Type oldType = currentType;
		final Type newType = Type.getObjectType(className);
		currentType = newType;

		final Method newMethod = new Method(String.format("group_%d_%d_new", oliveId, steps.size()), newType,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type), Stream.of(oldType))
						.toArray(Type[]::new));
		final Method collectMethod = new Method(String.format("group_%d_%d_collect", oliveId, steps.size()), VOID_TYPE,
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
			renderer.methodGen().invokeDynamic(
					"test", Type.getMethodDescriptor(A_PREDICATE_TYPE), LAMBDA_METAFACTORY_BSM,
					Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), new Handle(Opcodes.H_INVOKEVIRTUAL, className,
							"$isOk", Type.getMethodDescriptor(BOOLEAN_TYPE), false),
					Type.getMethodType(BOOLEAN_TYPE, newType));
			renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FILTER);
		});

		final Renderer newMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, newMethod, null, null, owner.classVisitor),
				capturedVariables.length, oldType, RootBuilder.proxyCaptured(0, capturedVariables), this::emitSigner);
		final Renderer collectedMethodGen = new Renderer(owner,
				new GeneratorAdapter(Opcodes.ACC_PUBLIC, collectMethod, null, null, owner.classVisitor),
				capturedVariables.length + 1, oldType, RootBuilder.proxyCaptured(0, capturedVariables),
				this::emitSigner);

		return new RegroupVariablesBuilder(owner, className, newMethodGen, collectedMethodGen,
				capturedVariables.length);
	}
}
