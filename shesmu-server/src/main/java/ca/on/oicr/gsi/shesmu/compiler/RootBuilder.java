package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionConsumer;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.Dumper;
import ca.on.oicr.gsi.shesmu.DumperSource;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import io.prometheus.client.Gauge;

/**
 * Helper to build an {@link ActionGenerator}
 */
public abstract class RootBuilder {

	private static final Type A_ACTION_CONSUMER_TYPE = Type.getType(ActionConsumer.class);
	private static final Type A_ACTION_GENERATOR_TYPE = Type.getType(ActionGenerator.class);
	private static final Type A_DUMPER_SOURCE_TYPE = Type.getType(DumperSource.class);
	private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
	private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
	private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);

	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method CTOR_CLASS = new Method("<clinit>", VOID_TYPE, new Type[] {});
	private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});
	private static final Handle HANDLER_IMYHAT = new Handle(Opcodes.H_INVOKESTATIC, A_IMYHAT_TYPE.getInternalName(),
			"bootstrap", Type.getMethodDescriptor(Type.getType(CallSite.class),
					Type.getType(MethodHandles.Lookup.class), A_STRING_TYPE, Type.getType(MethodType.class)),
			false);
	private static final Method METHOD_ACTION_GENERATOR__CLEAR_GAUGE = new Method("clearGauge", VOID_TYPE,
			new Type[] {});
	private static final Method METHOD_ACTION_GENERATOR__RUN = new Method("run", VOID_TYPE,
			new Type[] { A_ACTION_CONSUMER_TYPE, A_FUNCTION_TYPE });

	private static final Method METHOD_BUILD_GAUGE = new Method("buildGauge", A_GAUGE_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_ARRAY_TYPE });
	private final static Method METHOD_DUMPER__FIND = new Method("find", A_DUMPER_TYPE,
			new Type[] { A_STRING_TYPE, Type.getType(Imyhat[].class) });
	private final static Method METHOD_DUMPER__START = new Method("start", VOID_TYPE, new Type[] {});
	private final static Method METHOD_DUMPER__STOP = new Method("stop", VOID_TYPE, new Type[] {});
	private static final Method METHOD_GAUGE__CLEAR = new Method("clear", VOID_TYPE, new Type[] {});

	private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);

	public static Stream<LoadableValue> proxyCaptured(int offset, LoadableValue... capturedVariables) {
		return IntStream.range(0, capturedVariables.length).boxed().map(index -> new LoadableValue() {

			@Override
			public void accept(Renderer renderer) {
				renderer.methodGen().loadArg(index + offset);
			}

			@Override
			public String name() {
				return capturedVariables[index].name();
			}

			@Override
			public Type type() {
				return capturedVariables[index].type();
			}
		});
	}

	final GeneratorAdapter classInitMethod;
	final ClassVisitor classVisitor;
	private final GeneratorAdapter clearGaugeMethod;

	final long compileTime;

	private final Supplier<Stream<ConstantDefinition>> constants;

	private final GeneratorAdapter ctor;

	private final Set<String> dumpers = new HashSet<>();

	private final Set<String> gauges = new HashSet<>();

	private final InputFormatDefinition inputFormatDefinition;

	private final String path;

	private final GeneratorAdapter runMethod;

	private final Label runStartLabel;
	private final Type selfType;

	private final List<LoadableValue> userDefinedConstants = new ArrayList<>();

	public RootBuilder(Instant compileTime, String name, String path, InputFormatDefinition inputFormatDefinition,
			Supplier<Stream<ConstantDefinition>> constants) {
		this.compileTime = compileTime.toEpochMilli();
		this.path = path;
		this.inputFormatDefinition = inputFormatDefinition;
		this.constants = constants;
		selfType = Type.getObjectType(name);

		classVisitor = createClassVisitor();
		classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, A_ACTION_GENERATOR_TYPE.getInternalName(),
				null);
		classVisitor.visitSource(path, null);
		ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_DEFAULT, null, null, classVisitor);
		ctor.visitCode();
		ctor.loadThis();
		ctor.invokeConstructor(A_ACTION_GENERATOR_TYPE, CTOR_DEFAULT);

		clearGaugeMethod = new GeneratorAdapter(Opcodes.ACC_PRIVATE, METHOD_ACTION_GENERATOR__CLEAR_GAUGE, null, null,
				classVisitor);
		clearGaugeMethod.visitCode();

		runMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNCHRONIZED, METHOD_ACTION_GENERATOR__RUN,
				null, null, classVisitor);
		runMethod.visitCode();
		runMethod.loadThis();
		runMethod.invokeVirtual(selfType, METHOD_ACTION_GENERATOR__CLEAR_GAUGE);
		runStartLabel = runMethod.mark();

		classInitMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, CTOR_CLASS, null, null,
				classVisitor);
		classInitMethod.visitCode();
	}

	/**
	 * Create a new “Define” olive
	 */
	public OliveDefineBuilder buildDefineOlive(String name, Stream<? extends Target> parameters) {
		return new OliveDefineBuilder(this, name, parameters);
	}

	/**
	 * Create a new “Run” olive
	 *
	 * @param line
	 *            the line in the source file this olive starts on
	 * @param signableNames
	 */
	public final OliveBuilder buildRunOlive(int line, int column, Set<String> signableNames) {
		return new OliveBuilder(this, inputFormatDefinition.type(), line, column,
				inputFormatDefinition.baseStreamVariables()
						.filter(t -> t.flavour() == Flavour.STREAM_SIGNABLE && signableNames.contains(t.name())));
	}

	public Stream<LoadableValue> constants(boolean allowUserDefined) {
		final Stream<LoadableValue> externalConstants = constants.get().map(ConstantDefinition::asLoadable);
		return allowUserDefined ? Stream.concat(userDefinedConstants.stream(), externalConstants) : externalConstants;
	}

	/**
	 * Create a new class for this program.
	 */
	protected abstract ClassVisitor createClassVisitor();

	public void defineConstant(String name, Type type, Consumer<GeneratorAdapter> loader) {
		final String fieldName = name + "$constant";
		classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, type.getDescriptor(), null, null).visitEnd();

		runMethod.loadThis();
		loader.accept(runMethod);
		runMethod.putField(selfType, fieldName, type);
		userDefinedConstants.add(new LoadableValue() {

			@Override
			public void accept(Renderer renderer) {
				renderer.methodGen().loadThis();
				renderer.methodGen().getField(selfType, fieldName, type);
			}

			@Override
			public String name() {
				return name;
			}

			@Override
			public Type type() {
				return type;
			}
		});
	}

	/**
	 * Complete bytecode generation.
	 */
	public final void finish() {
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();

		classInitMethod.visitInsn(Opcodes.RETURN);
		classInitMethod.visitMaxs(0, 0);
		classInitMethod.visitEnd();

		dumpers.forEach(dumper -> {
			clearGaugeMethod.loadThis();
			clearGaugeMethod.getField(selfType, dumper, A_DUMPER_TYPE);
			clearGaugeMethod.invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__START);

			runMethod.loadThis();
			runMethod.getField(selfType, dumper, A_DUMPER_TYPE);
			runMethod.invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__STOP);

		});
		final Label endOfRun = runMethod.mark();

		runMethod.visitInsn(Opcodes.RETURN);

		if (!dumpers.isEmpty()) {
			// Generate a finally block to clean up all our dumpers
			runMethod.catchException(runStartLabel, endOfRun, null);
			dumpers.forEach(dumper -> {
				runMethod.loadThis();
				runMethod.getField(selfType, dumper, A_DUMPER_TYPE);
				runMethod.invokeInterface(A_DUMPER_TYPE, METHOD_DUMPER__STOP);

			});
			runMethod.throwException();
		}
		runMethod.visitMaxs(0, 0);
		runMethod.visitEnd();

		gauges.forEach(gauge -> {
			clearGaugeMethod.loadThis();
			clearGaugeMethod.getField(selfType, gauge, A_GAUGE_TYPE);
			clearGaugeMethod.invokeVirtual(A_GAUGE_TYPE, METHOD_GAUGE__CLEAR);
		});
		clearGaugeMethod.visitInsn(Opcodes.RETURN);
		clearGaugeMethod.visitMaxs(0, 0);
		clearGaugeMethod.visitEnd();

		classVisitor.visitEnd();
	}

	public InputFormatDefinition inputFormatDefinition() {
		return inputFormatDefinition;
	}

	public void loadDumper(String dumper, GeneratorAdapter methodGen, Imyhat... types) {
		final String fieldName = "d$" + dumper;
		if (!dumpers.contains(fieldName)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, A_DUMPER_TYPE.getDescriptor(), null, null)
					.visitEnd();
			ctor.loadThis();
			ctor.push(dumper);
			ctor.push(types.length);
			ctor.newArray(A_IMYHAT_TYPE);
			for (int i = 0; i < types.length; i++) {
				ctor.dup();
				ctor.push(i);
				ctor.invokeDynamic(types[i].descriptor(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
				ctor.arrayStore(A_IMYHAT_TYPE);
			}
			ctor.visitMethodInsn(Opcodes.INVOKESTATIC, A_DUMPER_SOURCE_TYPE.getInternalName(),
					METHOD_DUMPER__FIND.getName(), METHOD_DUMPER__FIND.getDescriptor(), true);
			ctor.putField(selfType, fieldName, A_DUMPER_TYPE);
			dumpers.add(fieldName);
		}
		methodGen.loadThis();
		methodGen.getField(selfType, fieldName, A_DUMPER_TYPE);
	}

	public final void loadGauge(String metricName, String help, List<String> labelNames, GeneratorAdapter methodGen) {
		final String fieldName = "g$" + metricName;
		if (!gauges.contains(metricName)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, A_GAUGE_TYPE.getDescriptor(), null, null)
					.visitEnd();
			ctor.loadThis();
			ctor.loadThis();
			ctor.push(metricName);
			ctor.push(help);
			ctor.push(labelNames.size());
			ctor.newArray(A_STRING_TYPE);
			for (int i = 0; i < labelNames.size(); i++) {
				ctor.dup();
				ctor.push(i);
				ctor.push(labelNames.get(i));
				ctor.arrayStore(A_STRING_TYPE);
			}
			ctor.invokeVirtual(selfType, METHOD_BUILD_GAUGE);
			ctor.putField(selfType, fieldName, A_GAUGE_TYPE);
			gauges.add(fieldName);
		}
		methodGen.loadThis();
		methodGen.getField(selfType, fieldName, A_GAUGE_TYPE);
	}

	/**
	 * Get the renderer for {@link ActionGenerator#run(Consumer, Supplier)}
	 *
	 * No stream variables are available in this context
	 */
	public final Renderer rootRenderer(boolean allowUserDefined) {
		return new Renderer(this, runMethod, -1, null, constants(allowUserDefined), (renderer, name) -> {
			throw new IllegalArgumentException(
					String.format("Signature variable %s not defined in root context.", name));
		});
	}

	/**
	 * Get the type of the class being generated
	 */
	public final Type selfType() {
		return selfType;
	}

	public String sourcePath() {
		return path;
	}
}
