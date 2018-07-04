package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.VOID_TYPE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.Dumper;
import ca.on.oicr.gsi.shesmu.DumperSource;
import ca.on.oicr.gsi.shesmu.Variables;
import io.prometheus.client.Gauge;

/**
 * Helper to build an {@link ActionGenerator}
 */
public abstract class RootBuilder {

	private static final Type A_ACTION_GENERATOR_TYPE = Type.getType(ActionGenerator.class);
	private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
	private static final Type A_DUMPER_SOURCE_TYPE = Type.getType(DumperSource.class);
	private static final Type A_DUMPER_TYPE = Type.getType(Dumper.class);
	private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);
	private static final Type A_VARIABLES_TYPE = Type.getType(Variables.class);

	private static final Method CTOR_CLASS = new Method("<clinit>", VOID_TYPE, new Type[] {});
	private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});

	private static final Method METHOD_ACTION_GENERATOR__CLEAR_GAUGE = new Method("clearGauge", VOID_TYPE,
			new Type[] {});
	private static final Method METHOD_ACTION_GENERATOR__RUN = new Method("run", VOID_TYPE,
			new Type[] { A_CONSUMER_TYPE, A_SUPPLIER_TYPE });
	private static final Method METHOD_BUILD_GAUGE = new Method("buildGauge", A_GAUGE_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_ARRAY_TYPE });
	private final static Method METHOD_DUMPER__FIND = new Method("find", A_DUMPER_TYPE, new Type[] { A_STRING_TYPE });

	private final static Method METHOD_DUMPER__START = new Method("start", VOID_TYPE, new Type[] {});

	private final static Method METHOD_DUMPER__STOP = new Method("stop", VOID_TYPE, new Type[] {});
	private static final Method METHOD_GAUGE__CLEAR = new Method("clear", VOID_TYPE, new Type[] {});

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

	private final GeneratorAdapter classInitMethod;
	final ClassVisitor classVisitor;
	private final GeneratorAdapter clearGaugeMethod;
	private final Supplier<Stream<Constant>> constants;
	private final GeneratorAdapter ctor;

	private final Set<String> dumpers = new HashSet<>();

	private final Set<String> gauges = new HashSet<>();
	private int oliveId = 0;

	private final String path;

	private final GeneratorAdapter runMethod;

	private final Type selfType;

	private int streamId;

	public RootBuilder(String name, String path, Supplier<Stream<Constant>> constants) {
		this.path = path;
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

		runMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_ACTION_GENERATOR__RUN, null, null, classVisitor);
		runMethod.visitCode();
		runMethod.loadThis();
		runMethod.invokeVirtual(selfType, METHOD_ACTION_GENERATOR__CLEAR_GAUGE);

		classInitMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_CLASS, null, null, classVisitor);
		classInitMethod.visitCode();
	}

	/**
	 * Create a new “Define” olive
	 */
	public OliveDefineBuilder buildDefineOlive(Stream<? extends Target> parameters) {
		return new OliveDefineBuilder(this, oliveId++, parameters);
	}

	/**
	 * Create a new “Run” olive
	 *
	 * @param line
	 *            the line in the source file this olive starts on
	 */
	public final OliveBuilder buildRunOlive(int line) {
		return new OliveBuilder(this, oliveId++, A_VARIABLES_TYPE, line);
	}

	public Stream<LoadableValue> constants() {
		return constants.get().map(Constant::asLoadable);
	}

	/**
	 * Create a new class for this program.
	 */
	protected abstract ClassVisitor createClassVisitor();

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

		runMethod.visitInsn(Opcodes.RETURN);
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

	public void loadDumper(String dumper, GeneratorAdapter methodGen) {
		final String fieldName = "d$" + dumper;
		if (!dumpers.contains(fieldName)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, A_DUMPER_TYPE.getDescriptor(), null, null)
					.visitEnd();
			ctor.loadThis();
			ctor.push(dumper);
			ctor.invokeStatic(A_DUMPER_SOURCE_TYPE, METHOD_DUMPER__FIND);
			ctor.putField(selfType, fieldName, A_DUMPER_TYPE);

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
		}
		methodGen.loadThis();
		methodGen.getField(selfType, fieldName, A_GAUGE_TYPE);
	}

	int nextStreamId() {
		return streamId++;
	}

	/**
	 * Get the renderer for {@link ActionGenerator#run(Consumer, Supplier)}
	 *
	 * No stream variables are available in this context
	 */
	public final Renderer rootRenderer() {
		return new Renderer(this, runMethod, -1, null, Stream.empty());
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
