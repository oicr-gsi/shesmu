package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

/**
 * A collection action in a “Group” clause
 *
 * Also usable as the variable definition for the result
 */
public final class GroupNode extends Target {
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

	private final int column;
	private final ExpressionNode expression;
	private final int line;
	private final String name;

	public GroupNode(int line, int column, String name, ExpressionNode expression) {
		this.line = line;
		this.column = column;
		this.name = name;
		this.expression = expression;
	}

	public int column() {
		return column;
	}

	public ExpressionNode expression() {
		return expression;
	}

	@Override
	public Flavour flavour() {
		return Flavour.STREAM;
	}

	public int line() {
		return line;
	}

	@Override
	public String name() {
		return name;
	}

	public void render(RegroupVariablesBuilder regroup, RootBuilder rootBuilder) {
		regroup.addCollected(expression.type().asmType(), name, context -> expression.render(context));
	}

	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return expression.type().asList();
	}

	public boolean typeCheck(Consumer<String> errorHandler) {
		return expression.typeCheck(errorHandler);
	}
}
