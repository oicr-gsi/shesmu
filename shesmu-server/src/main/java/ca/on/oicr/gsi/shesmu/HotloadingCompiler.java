package ca.on.oicr.gsi.shesmu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import ca.on.oicr.gsi.shesmu.compiler.Compiler;

/**
 * Compiles a user-specified file into a useable program and updates it as
 * necessary
 */
public final class HotloadingCompiler {
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

	private final Supplier<Stream<Constant>> constants;

	private final List<String> errors = new ArrayList<>();

	private final Supplier<Stream<Lookup>> lookups;

	public HotloadingCompiler(Supplier<Stream<Lookup>> lookups, Supplier<Stream<ActionDefinition>> actions,
			Supplier<Stream<Constant>> constants) {
		this.lookups = lookups;
		this.actions = actions;
		this.constants = constants;
	}

	public Optional<ActionGenerator> compile(Path fileName) {
		try {
			errors.clear();
			final Compiler compiler = new Compiler(false) {
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
					errors.add(message);
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
			if (compiler.compile(Files.readAllBytes(fileName), "dyn/shesmu/Program", fileName.toString(), constants)) {
				return Optional.of(
						classloader.loadClass("dyn.shesmu.Program").asSubclass(ActionGenerator.class).newInstance());
			}
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	public Stream<String> errors() {
		return errors.stream();
	}

}
