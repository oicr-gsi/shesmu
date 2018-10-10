package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.core.actions.rest.FileActionRepository;
import ca.on.oicr.gsi.shesmu.core.tsv.TableFunctionRepository;
import ca.on.oicr.gsi.shesmu.util.FileWatcher;
import ca.on.oicr.gsi.shesmu.util.NameLoader;

public class CompilerTest {
	public final class CompilerHarness extends Compiler {
		private Set<String> allowedErrors;
		private boolean dirty;

		public CompilerHarness(Path file) throws IOException {
			super(false);
			try (Stream<String> lines = Files.lines(
					file.getParent().resolve(file.getFileName().toString().replaceFirst("\\.shesmu", ".errors")))) {
				allowedErrors = lines.collect(Collectors.toSet());
			}
		}

		@Override
		protected ClassVisitor createClassVisitor() {
			final ClassWriter outputWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
			return new ClassVisitor(Opcodes.ASM5, outputWriter) {

				@Override
				public void visitEnd() {
					super.visitEnd();
					final ClassReader reader = new ClassReader(outputWriter.toByteArray());
					final CheckClassAdapter check = new CheckClassAdapter(new ClassWriter(0), true);
					reader.accept(check, 0);
				}
			};
		}

		@Override
		protected void errorHandler(String message) {
			if (allowedErrors.remove(message)) {
				return;
			}
			dirty = true;
			System.err.println(message);
		}

		@Override
		protected ActionDefinition getAction(String name) {
			return actions.get(name);
		}

		@Override
		protected FunctionDefinition getFunction(String name) {
			return functions.get(name);
		}

		@Override
		protected InputFormatDefinition getInputFormats(String name) {
			return RunTest.INPUT_FORMATS.get(name);
		}

		public boolean ok() {
			allowedErrors.forEach(e -> System.err.printf("Missing error: %s\n", e));
			return !dirty && allowedErrors.isEmpty();
		}

	}

	private static final List<Constant> CONSTANTS = Arrays.asList(
			Constant.of("alwaystrue", true, "It's true. I swear."),
			Constant.of("notpi", 3, "Any value which is not pi."));

	private static final FileWatcher TEST_WATCHER = FileWatcher
			.of(Paths.get(CompilerTest.class.getResource("/data").getPath()));

	private final NameLoader<ActionDefinition> actions = new NameLoader<>(
			new FileActionRepository(TEST_WATCHER).queryActions(), ActionDefinition::name);
	private final NameLoader<FunctionDefinition> functions = new NameLoader<>(
			new TableFunctionRepository(TEST_WATCHER).queryFunctions(), FunctionDefinition::name);

	@Test
	public void testCompiler() throws IOException {
		try (Stream<Path> files = Files.walk(Paths.get(this.getClass().getResource("/compiler").getPath()), 1)) {
			Assert.assertTrue("Compilation output not as expected!", files//
					.filter(Files::isRegularFile)//
					.filter(p -> p.getFileName().getFileName().toString().endsWith(".shesmu"))//
					.map(this::testFile)//
					.filter(Pair.predicate((file, ok) -> {
						System.err.printf("%s %s\n", ok ? "OK" : "FAIL", file.getFileName());
						return !ok;
					}))//
					.count() == 0);
		}
	}

	private Pair<Path, Boolean> testFile(Path file) {
		try {
			final CompilerHarness compiler = new CompilerHarness(file);
			// Attempt to compile and throw away whether the compiler was successful; we
			// know everything based on the errors generated.
			compiler.compile(Files.readAllBytes(file), "dyn/shesmu/Program", file.toString(), CONSTANTS::stream, null);
			return new Pair<>(file, compiler.ok());
		} catch (final Exception e) {
			return new Pair<>(file, false);
		}
	}
}
