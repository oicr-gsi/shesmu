package ca.on.oicr.gsi.shesmu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import io.prometheus.client.Gauge;

/**
 * Compiles a user-specified file into a useable program and updates it as
 * necessary
 */
public class CompiledGenerator extends AutoUpdatingFile {
	final class WritingClassVisitor extends ClassVisitor {

		private String className;

		private final ClassWriter writer;

		private WritingClassVisitor(ClassWriter writer) {
			super(Opcodes.ASM5, writer);
			this.writer = writer;
		}

		@Override
		public void visit(int version, int access, String className, String signature, String super_name,
				String[] interfaces) {
			this.className = className;
			super.visit(version, access, className, signature, super_name, interfaces);
		}

		@Override
		public void visitEnd() {
			super.visitEnd();
			bytecode.put(className.replace('/', '.'), writer.toByteArray());
		}
	}

	private static final Gauge compileTime = Gauge
			.build("shesmu_source_compile_time", "The number of seconds the last compilation took to perform.")
			.labelNames("filename").register();

	private static final Gauge sourceValid = Gauge
			.build("shesmu_source_valid", "Whether the source file has been successfully compiled.")
			.labelNames("filename").register();
	private final Supplier<Stream<ActionDefinition>> actions;

	private final Map<String, byte[]> bytecode = new HashMap<>();

	private final ClassLoader classloader = new ClassLoader() {

		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException {
			if (bytecode.containsKey(name)) {
				final byte[] contents = bytecode.get(name);
				return defineClass(name, contents, 0, contents.length);
			}
			return super.findClass(name);
		}

	};

	private final StringBuilder errors = new StringBuilder();
	private ActionGenerator generator = ActionGenerator.NULL;

	private final Supplier<Stream<Lookup>> lookups;

	public CompiledGenerator(Path fileName, Supplier<Stream<Lookup>> lookups,
			Supplier<Stream<ActionDefinition>> actions) {
		super(fileName);
		this.lookups = lookups;
		this.actions = actions;
	}

	private void compile() {
		try {
			errors.setLength(0);
			final Compiler compiler = new Compiler() {
				private final NameLoader<ActionDefinition> actionCache = new NameLoader<>(actions.get(),
						ActionDefinition::name);
				private final NameLoader<Lookup> lookupCache = new NameLoader<>(lookups.get(), Lookup::name);

				@Override
				protected ClassVisitor createClassVisitor() {
					return new WritingClassVisitor(
							new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS));
				}

				@Override
				protected void errorHandler(String message) {
					errors.append(message).append("<br/>");
				}

				@Override
				protected ActionDefinition getAction(String name) {
					return actionCache.get(name);
				}

				@Override
				protected Lookup getLookup(String lookup) {
					return lookupCache.get(lookup);
				}
			};

			bytecode.clear();
			if (compiler.compile(Files.readAllBytes(fileName()), "dyn/shesmu/Program", fileName().toString())) {
				generator = classloader.loadClass("dyn.shesmu.Program").asSubclass(ActionGenerator.class).newInstance();
				sourceValid.labels(fileName().toString()).set(1);
			} else {
				System.err.println("Bad compilation.");
				sourceValid.labels(fileName().toString()).set(0);
			}
		} catch (final Exception e) {
			e.printStackTrace();
			sourceValid.labels(fileName().toString()).set(0);
		}
	}

	public String errorHtml() {
		return errors.toString();
	}

	/**
	 * Get the last successfully compiled action generator.
	 */
	public ActionGenerator generator() {
		return generator;
	}

	@Override
	protected void update() {
		compileTime.labels(fileName().toString()).setToTime(this::compile);
	}

}
