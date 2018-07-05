package ca.on.oicr.gsi.shesmu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;

import ca.on.oicr.gsi.shesmu.compiler.Compiler;

/**
 * Compiles a user-specified file into a usable program and updates it as
 * necessary
 */
public final class HotloadingCompiler extends BaseHotloadingCompiler {

	private final Supplier<Stream<ActionDefinition>> actions;

	private final Supplier<Stream<Constant>> constants;

	private final List<String> errors = new ArrayList<>();

	private final Supplier<Stream<FunctionDefinition>> functions;

	private final Function<String, InputFormatDefinition> inputFormats;

	public HotloadingCompiler(Function<String, InputFormatDefinition> inputFormats,
			Supplier<Stream<FunctionDefinition>> functions, Supplier<Stream<ActionDefinition>> actions,
			Supplier<Stream<Constant>> constants) {
		this.inputFormats = inputFormats;
		this.functions = functions;
		this.actions = actions;
		this.constants = constants;
	}

	public Optional<ActionGenerator> compile(Path fileName) {
		try {
			errors.clear();
			final Compiler compiler = new Compiler(false) {
				private final NameLoader<ActionDefinition> actionCache = new NameLoader<>(actions.get(),
						ActionDefinition::name);
				private final NameLoader<FunctionDefinition> functionCache = new NameLoader<>(functions.get(),
						FunctionDefinition::name);

				@Override
				protected ClassVisitor createClassVisitor() {
					return HotloadingCompiler.this.createClassVisitor();
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
				protected FunctionDefinition getFunction(String function) {
					return functionCache.get(function);
				}

				@Override
				protected InputFormatDefinition getInputFormats(String name) {
					return inputFormats.apply(name);
				}
			};

			if (compiler.compile(Files.readAllBytes(fileName), "dyn/shesmu/Program", fileName.toString(), constants)) {
				return Optional.of(load(ActionGenerator.class, "dyn.shesmu.Program"));
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
