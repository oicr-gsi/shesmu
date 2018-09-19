package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

/**
 * The arguments defined in the “With” section of a “Run” olive.
 */
public abstract class OliveArgumentNode {
	public static Parser parse(Parser input, Consumer<OliveArgumentNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
		final AtomicReference<ExpressionNode> condition = new AtomicReference<>();

		final Parser result = input//
				.whitespace()//
				.identifier(name::set)//
				.whitespace()//
				.symbol("=")//
				.whitespace()//
				.then(ExpressionNode::parse, expression::set)//
				.whitespace();
		final Parser conditionResult = result.keyword("If")//
				.whitespace()//
				.then(ExpressionNode::parse, condition::set)//
				.whitespace();
		if (conditionResult.isGood()) {
			output.accept(new OliveArgumentNodeOptional(input.line(), input.column(), name.get(), condition.get(),
					expression.get()));
			return conditionResult;
		}
		if (result.isGood()) {
			output.accept(new OliveArgumentNodeProvided(input.line(), input.column(), name.get(), expression.get()));
		}
		return result;
	}

	protected final int column;
	protected final int line;

	protected final String name;

	public OliveArgumentNode(int line, int column, String name) {
		this.line = line;
		this.column = column;
		this.name = name;
	}

	public abstract void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate);

	/**
	 * Produce an error if the type of the expression is not as required
	 */
	public abstract boolean ensureType(ParameterDefinition definition, Consumer<String> errorHandler);

	/**
	 * The argument name
	 */
	public final String name() {
		return name;
	}

	/**
	 * Generate bytecode for this argument's value
	 */
	public abstract void render(Renderer renderer, int action);

	/**
	 * Resolve variables in the expression of this argument
	 */
	public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

	/**
	 * Resolve functions in this argument
	 */
	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler);

	public abstract Imyhat type();

	/**
	 * Perform type check on this argument's expression
	 */
	public abstract boolean typeCheck(Consumer<String> errorHandler);
}
