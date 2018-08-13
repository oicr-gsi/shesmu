package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * A collection action in a “Group” clause
 *
 * Also usable as the variable definition for the result
 */
public final class GroupNode extends ByChildNode {
	public static Parser parse(Parser input, Consumer<GroupNode> output) {
		final AtomicReference<String> name = new AtomicReference<>();
		final AtomicReference<ExpressionNode> expression = new AtomicReference<>();

		final Parser result = ExpressionNode
				.parse(input.identifier(name::set).whitespace().keyword("=").whitespace(), expression::set)
				.whitespace();
		if (result.isGood()) {
			output.accept(new GroupNode(input.line(), input.column(), name.get(), expression.get()));
		}
		return result;

	}

	private final ExpressionNode expression;

	public GroupNode(int line, int column, String name, ExpressionNode expression) {
		super(line, column, name);
		this.expression = expression;
	}

	public void collectFreeVariables(Set<String> freeVariables) {
		expression.collectFreeVariables(freeVariables);
	}

	public void render(RegroupVariablesBuilder regroup, RootBuilder rootBuilder) {
		regroup.addCollected(expression.type(), name(), expression::render);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
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
		return expression.type().asList();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}
}
