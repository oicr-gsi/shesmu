package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import ca.on.oicr.gsi.shesmu.actions.rest.FileActionRepository;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.lookup.TsvLookupRepository;

public class CompilerTest {
	public final class CompilerHarness extends Compiler {
		private boolean dirty;

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
			dirty = true;
		}

		@Override
		protected ActionDefinition getAction(String name) {
			return actions.get(name);
		}

		@Override
		protected Lookup getLookup(String name) {
			return lookups.get(name);
		}

		public boolean ok() {
			return !dirty;
		}

	}

	private final NameLoader<ActionDefinition> actions = new NameLoader<>(
			FileActionRepository.of(Optional.of(this.getClass().getResource("/data").getPath())),
			ActionDefinition::name);
	private final NameLoader<Lookup> lookups = new NameLoader<>(
			TsvLookupRepository.of(Optional.of(this.getClass().getResource("/data").getPath())), Lookup::name);

	@Test
	public void testBad() throws IOException {
		System.err.println("Testing bad code");
		try (Stream<Path> files = Files.walk(Paths.get(this.getClass().getResource("/bad").getPath()), 1)) {
			Assert.assertTrue("Bad code compiled!", files//
					.filter(Files::isRegularFile)//
					.map(this::testFile)//
					.filter(Pair.predicate((file, result) -> {
						boolean failed = result.orElse(true);
						if (failed) {
							System.err.printf("NEGFAIL %s\n", file);
						}
						return failed;
					}))//
					.count() == 0);
		}
	}

	private Pair<Path, Optional<Boolean>> testFile(Path file) {
		CompilerHarness compiler = new CompilerHarness();
		try {
			return new Pair<>(file,
					Optional.of(compiler.compile(Files.readAllBytes(file), "dyn/shesmu/Program", file.toString())
							&& compiler.ok()));
		} catch (Exception e) {
			return new Pair<>(file, Optional.empty());
		}
	}

	@Test
	public void testGood() throws IOException {
		System.err.println("Testing good code");
		try (Stream<Path> files = Files.walk(Paths.get(this.getClass().getResource("/good").getPath()), 1)) {
			Assert.assertTrue("Good code failed to compile!", files//
					.filter(Files::isRegularFile)//
					.map(this::testFile)//
					.filter(Pair.predicate((file, result) -> {
						boolean failed = !result.orElse(false);
						if (failed) {
							System.err.printf("FAIL %s\n", file);
						}
						return failed;
					}))//
					.count() == 0);
		}
	}
}
