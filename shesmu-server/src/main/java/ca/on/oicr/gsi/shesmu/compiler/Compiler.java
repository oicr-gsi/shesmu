package ca.on.oicr.gsi.shesmu.compiler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionGenerator;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;

/**
 * A shell of a compiler that can output bytecode
 */
public abstract class Compiler {

	private class MaxParseError implements ErrorConsumer {
		private int column;
		private int line;
		private String message = "No error.";

		@Override
		public void raise(int line, int column, String errorMessage) {
			if (this.line < line || this.line == line && this.column <= column) {
				this.line = line;
				this.column = column;
				message = errorMessage;
			}
		}

		public void write() {
			errorHandler(String.format("%d:%d: %s", line, column, message));
		}
	}

	private final boolean skipRender;

	/**
	 * Create a new instance of this compiler
	 *
	 * @param skipRender
	 *            if true, no bytecode will be generated when compiler is called;
	 *            only parsing and checking
	 */
	public Compiler(boolean skipRender) {
		super();
		this.skipRender = skipRender;
	}

	/**
	 * Compile a program
	 *
	 * @param input
	 *            the bytes in the script
	 * @param name
	 *            the internal name of the class to generate; it will extend
	 *            {@link ActionGenerator}
	 * @param path
	 *            the source file's path for debugging information
	 * @return whether compilation was successful
	 */
	public final boolean compile(byte[] input, String name, String path, Supplier<Stream<Constant>> constants) {
		final AtomicReference<List<OliveNode>> program = new AtomicReference<>();
		final MaxParseError maxParseError = new MaxParseError();
		final boolean parseOk = OliveNode.parseFile(new String(input, StandardCharsets.UTF_8), program::set,
				maxParseError);
		if (!parseOk) {
			maxParseError.write();
		}
		if (parseOk && OliveNode.validate(program.get(), this::getFunction, this::getAction, this::errorHandler,
				constants)) {
			if (skipRender) {
				return true;
			}
			final RootBuilder builder = new RootBuilder(name, path, constants) {
				@Override
				protected ClassVisitor createClassVisitor() {
					return Compiler.this.createClassVisitor();
				}
			};
			OliveNode.render(builder, program.get());
			builder.finish();
			return true;
		}
		return false;
	}

	/**
	 * Create a new class visitor for bytecode generation.
	 */
	protected abstract ClassVisitor createClassVisitor();

	/**
	 * Report an error to the user.
	 */
	protected abstract void errorHandler(String message);

	/**
	 * Get an action by name.
	 *
	 * @param name
	 *            the name of the action
	 * @return the action definition, or null if no action is available
	 */
	protected abstract ActionDefinition getAction(String name);

	/**
	 * Get a function by name.
	 *
	 * @param name
	 *            the name of the function
	 * @return the function or null if no function is available
	 */
	protected abstract FunctionDefinition getFunction(String name);
}
