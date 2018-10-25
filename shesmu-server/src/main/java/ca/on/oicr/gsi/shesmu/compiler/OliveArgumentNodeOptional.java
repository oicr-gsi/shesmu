package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

/**
 * The arguments defined in the “With” section of a “Run” olive.
 */
public final class OliveArgumentNodeOptional extends OliveArgumentNode {

	private final ExpressionNode condition;
	private ActionParameterDefinition definition;
	private final ExpressionNode expression;

	public OliveArgumentNodeOptional(int line, int column, String name, ExpressionNode condition,
			ExpressionNode expression) {
		super(line, column, name);
		this.condition = condition;
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
		expression.collectFreeVariables(freeVariables, predicate);
		condition.collectFreeVariables(freeVariables, predicate);
	}

	/**
	 * Produce an error if the type of the expression is not as required
	 */
	@Override
	public final boolean ensureType(ActionParameterDefinition definition, Consumer<String> errorHandler) {
		this.definition = definition;
		boolean ok = true;
		if (!definition.type().isSame(expression.type())) {
			errorHandler.accept(String.format("%d:%d: Expected argument “%s” to have type %s, but got %s.", line,
					column, name, definition.type().name(), expression.type().name()));
			ok = false;
		}
		if (!Imyhat.BOOLEAN.isSame(condition.type())) {
			errorHandler.accept(String.format("%d:%d: Condition for argument “%s” must be boolean, but got %s.", line,
					column, name, condition.type().name()));
			ok = false;
		}
		if (definition.required()) {
			errorHandler.accept(String.format("%d:%d: Argument “%s” is required.", line, column, name));
			ok = false;
		}
		return ok;
	}

	/**
	 * Generate bytecode for this argument's value
	 */
	@Override
	public void render(Renderer renderer, int action) {
		condition.render(renderer);
		renderer.mark(line);
		final Label end = renderer.methodGen().newLabel();
		renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, end);
		definition.store(renderer, renderer.methodGen().getLocalType(action), action, expression::render);
		renderer.methodGen().mark(end);
	}

	/**
	 * Resolve variables in the expression of this argument
	 */
	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler) & condition.resolve(defs, errorHandler);
	}

	/**
	 * Resolve functions in this argument
	 */
	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler)
				& condition.resolveFunctions(definedFunctions, errorHandler);

	}

	@Override
	public Imyhat type() {
		return expression.type();
	}

	/**
	 * Perform type check on this argument's expression
	 */
	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler) & condition.typeCheck(errorHandler);
	}

}
