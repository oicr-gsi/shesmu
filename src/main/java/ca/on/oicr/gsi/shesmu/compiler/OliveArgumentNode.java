package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

/**
 * The arguments defined in the “With” section of a “Run” olive or “Monitor” clause.
 */
public final class OliveArgumentNode {
	public static Parser parse(Parser input, Consumer<OliveArgumentNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ExpressionNode> expression = new AtomicReference<>();

		final Parser result = input//
				.whitespace()//
				.identifier(name::set)//
				.whitespace()//
				.keyword("=")//
				.whitespace()//
				.then(ExpressionNode::parse, expression::set)//
				.whitespace();
		if (result.isGood()) {
			output.accept(new OliveArgumentNode(input.line(), input.column(), name.get(), expression.get()));
		}
		return result;
	}

	private final int column;
	private final ExpressionNode expression;
	private final int line;

	private final String name;

	public OliveArgumentNode(int line, int column, String name, ExpressionNode expression) {
		this.line = line;
		this.column = column;
		this.name = name;
		this.expression = expression;
	}

	public void collectFreeVariables(Set<String> freeVariables) {
		expression.collectFreeVariables(freeVariables);
	}

	/**
	 * Produce an error if the type of the expression is not as required
	 *
	 * @param targetType
	 *            the required type
	 */
	public boolean ensureType(Imyhat targetType, Consumer<String> errorHandler) {
		final boolean ok = targetType.isSame(type());
		if (!ok) {
			errorHandler.accept(String.format("%d:%d: Expected argument “%s” to have type %s, but got %s.", line,
					column, name, targetType.name(), type().name()));
		}
		return ok;
	}

	/**
	 * The argument name
	 */
	public String name() {
		return name;
	}

	/**
	 * Generate bytecode for this argument's value
	 */
	public void render(Renderer renderer) {
		expression.render(renderer);
	}

	/**
	 * Resolve variables in the expression of this argument
	 */
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	/**
	 * Resolve lookups in this argument
	 */
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);

	}

	/**
	 * Get the type of this expression
	 */
	public Imyhat type() {
		return expression.type();
	}

	/**
	 * Perform type check on this argument's expression
	 */
	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}
}
