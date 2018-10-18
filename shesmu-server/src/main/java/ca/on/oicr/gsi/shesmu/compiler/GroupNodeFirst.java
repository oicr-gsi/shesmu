package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A “First” collector
 *
 * Also usable as the variable definition for the result
 */
public final class GroupNodeFirst extends GroupNodeDefaultable {

	private final ExpressionNode expression;
	private final String name;

	public GroupNodeFirst(int line, int column, String name, ExpressionNode expression) {
		super(line, column);
		this.name = name;
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
		expression.collectFreeVariables(freeVariables, predicate);
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public void render(Regrouper regroup, RootBuilder rootBuilder) {
		regroup.addFirst(expression.type().asmType(), name(), expression::render);
	}

	@Override
	public void render(Regrouper regroup, ExpressionNode initial, RootBuilder rootBuilder) {
		regroup.addFirst(expression.type().asmType(), name(), expression::render, initial::render);
	}

	@Override
	public boolean resolve(NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}
}
