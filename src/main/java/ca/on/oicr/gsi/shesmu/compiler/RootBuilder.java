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
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.NameLoader;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Variables;
import io.prometheus.client.Gauge;

/**
 * Helper to build an {@link ActionGenerator}
 */
public abstract class RootBuilder {

	private static final Type A_ACTION_GENERATOR_TYPE = Type.getType(ActionGenerator.class);
	private static final Type A_CHAR_SEQ_TYPE = Type.getType(CharSequence.class);
	private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
	private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	private static final Type A_LOOKUP_TYPE = Type.getType(Lookup.class);
	private static final Type A_NAME_LOADER_TYPE = Type.getType(NameLoader.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_RUNTIME_SUPPORT = Type.getType(RuntimeSupport.class);
	private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);
	private static final Type A_VARIABLES_TYPE = Type.getType(Variables.class);

	private static final Method CTOR_CLASS = new Method("<clinit>", VOID_TYPE, new Type[] {});
	private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});

	private static final Method METHOD_ACTION_GENERATOR__CLEAR_GAUGE = new Method("clearGauge", VOID_TYPE,
			new Type[] {});
	private static final Method METHOD_ACTION_GENERATOR__POPULATE_LOOKUPS = new Method("populateLookups", VOID_TYPE,
			new Type[] { A_NAME_LOADER_TYPE });
	private static final Method METHOD_ACTION_GENERATOR__RUN = new Method("run", VOID_TYPE,
			new Type[] { A_CONSUMER_TYPE, A_SUPPLIER_TYPE });
	private static final Method METHOD_GAUGE__CLEAR = new Method("clear", VOID_TYPE, new Type[] {});
	private static final Method METHOD_IMYHAT__PARSE = new Method("parse", A_IMYHAT_TYPE,
			new Type[] { A_CHAR_SEQ_TYPE });
	private static final Method METHOD_NAME_LOADER__GET = new Method("get", A_OBJECT_TYPE,
			new Type[] { A_STRING_TYPE });
	private static final Method METHOD_RUNTIME_SUPPORT__BUILD_GAUGE = new Method("buildGauge", A_GAUGE_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE, A_STRING_ARRAY_TYPE });
	private static final Method METHOD_RUNTIME_SUPPORT__RESET_GAUGES = new Method("resetGauges", VOID_TYPE,
			new Type[] {});

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
	private final GeneratorAdapter ctor;
	private final Set<String> gauges = new HashSet<>();
	private final Set<String> imyhats = new HashSet<>();
	private final Set<String> lookups = new HashSet<>();
	private int oliveId = 0;
	private final String path;
	private final GeneratorAdapter populateLookupsMethod;
	private final GeneratorAdapter runMethod;

	private final Type selfType;

	private int streamId;

	public RootBuilder(String name, String path) {
		this.path = path;
		selfType = Type.getObjectType(name);

		classVisitor = createClassVisitor();
		classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, A_ACTION_GENERATOR_TYPE.getInternalName(),
				null);
		classVisitor.visitSource(path, null);
		ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR_DEFAULT, null, null, classVisitor);
		ctor.visitCode();
		ctor.loadThis();
		ctor.invokeConstructor(A_ACTION_GENERATOR_TYPE, CTOR_DEFAULT);
		ctor.invokeStatic(A_RUNTIME_SUPPORT, METHOD_RUNTIME_SUPPORT__RESET_GAUGES);

		clearGaugeMethod = new GeneratorAdapter(Opcodes.ACC_PRIVATE, METHOD_ACTION_GENERATOR__CLEAR_GAUGE, null, null,
				classVisitor);
		clearGaugeMethod.visitCode();

		runMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_ACTION_GENERATOR__RUN, null, null, classVisitor);
		runMethod.visitCode();
		runMethod.loadThis();
		runMethod.invokeVirtual(selfType, METHOD_ACTION_GENERATOR__CLEAR_GAUGE);

		populateLookupsMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_ACTION_GENERATOR__POPULATE_LOOKUPS,
				null, null, classVisitor);
		populateLookupsMethod.visitCode();

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
	 */
	public final OliveBuilder buildRunOlive() {
		return new OliveBuilder(this, oliveId++, A_VARIABLES_TYPE);
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

		populateLookupsMethod.visitInsn(Opcodes.RETURN);
		populateLookupsMethod.visitMaxs(0, 0);
		populateLookupsMethod.visitEnd();

		classInitMethod.visitInsn(Opcodes.RETURN);
		classInitMethod.visitMaxs(0, 0);
		classInitMethod.visitEnd();

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

	public final void loadGauge(String metricName, String help, List<String> labelNames, GeneratorAdapter methodGen) {
		final String fieldName = "g$" + metricName;
		if (!gauges.contains(metricName)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, A_GAUGE_TYPE.getDescriptor(), null, null)
					.visitEnd();
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
			ctor.invokeStatic(A_RUNTIME_SUPPORT, METHOD_RUNTIME_SUPPORT__BUILD_GAUGE);
			ctor.putField(selfType, fieldName, A_GAUGE_TYPE);
		}
		methodGen.loadThis();
		methodGen.getField(selfType, fieldName, A_GAUGE_TYPE);
	}

	public void loadImyhat(String signature, GeneratorAdapter methodGen) {
		final String name = "imyhat$" + signature;
		if (!imyhats.contains(name)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name,
					A_IMYHAT_TYPE.getDescriptor(), null, null).visitEnd();

			classInitMethod.push(signature);
			classInitMethod.invokeStatic(A_IMYHAT_TYPE, METHOD_IMYHAT__PARSE);
			classInitMethod.putStatic(selfType, name, A_IMYHAT_TYPE);

			imyhats.add(name);
		}
		methodGen.getStatic(selfType, name, A_IMYHAT_TYPE);
	}

	/**
	 * Load a lookup into the method provided
	 *
	 * @param name
	 *            the name of the method
	 * @param methodGen
	 *            the method where the lookup should be loaded; it must be part of
	 *            this class (that is a method created by an olive defined here)
	 */
	public final void loadLookup(String name, GeneratorAdapter methodGen) {
		if (!lookups.contains(name)) {
			classVisitor.visitField(Opcodes.ACC_PRIVATE, name, A_LOOKUP_TYPE.getDescriptor(), null, null).visitEnd();

			populateLookupsMethod.loadThis();
			populateLookupsMethod.loadArg(0);
			populateLookupsMethod.push(name);
			populateLookupsMethod.invokeVirtual(A_NAME_LOADER_TYPE, METHOD_NAME_LOADER__GET);
			populateLookupsMethod.checkCast(A_LOOKUP_TYPE);
			populateLookupsMethod.putField(selfType, name, A_LOOKUP_TYPE);

			lookups.add(name);
		}
		methodGen.loadThis();
		methodGen.getField(selfType, name, A_LOOKUP_TYPE);
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
