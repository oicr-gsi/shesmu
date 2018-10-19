package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;

/**
 * An expression in the Shesmu language
 */
public abstract class DiscriminatorNode extends DefinedTarget {

	public static Parser parse(Parser input, Consumer<DiscriminatorNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final Parser baseParser = input//
				.whitespace()//
				.identifier(name::set)//
				.whitespace();
		if (baseParser.isGood()) {

			final Parser renameParser = baseParser.symbol("=");
			if (renameParser.isGood()) {
				final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
				final Parser renameResult = renameParser//
						.whitespace()//
						.then(ExpressionNode::parse, expression::set)//
						.whitespace();
				if (renameResult.isGood()) {
					output.accept(
							new DiscriminatorNodeRename(input.line(), input.column(), name.get(), expression.get()));
				}
				return renameResult;
			}
			output.accept(new DiscriminatorNodeSimple(input.line(), input.column(), name.get()));
		}
		return baseParser;
	}

	private final int column;

	private final int line;
public abstract VariableInformation dashboard();
	public DiscriminatorNode(int line, int column) {
		super();
		this.line = line;
		this.column = column;
	}

	/**
	 * Add all free variable names to the set provided.
	 */
	public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

	public int column() {
		return column;
	}

	public int line() {
		return line;
	}

	/**
	 * Produce bytecode for this discriminator
	 */
	public abstract void render(RegroupVariablesBuilder builder);

	/**
	 * Resolve all variable definitions in this discriminator and its children.
	 */
	public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve all function definitions in this discriminator
	 */
	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler);

	/**
	 * The type of this discriminator
	 *
	 * This should return {@link Imyhat#BAD} if no type can be determined
	 */
	public abstract Imyhat type();

	/**
	 * Perform type checking on this discriminator and its children.
	 *
	 * @param errorHandler
	 * @return
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
