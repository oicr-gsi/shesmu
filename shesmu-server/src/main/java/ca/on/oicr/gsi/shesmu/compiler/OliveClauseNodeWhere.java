package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodeWhere extends OliveClauseNode {

	private final int column;
	private final ExpressionNode expression;
	private final int line;

	public OliveClauseNodeWhere(int line, int column, ExpressionNode expression) {
		this.line = line;
		this.column = column;
		this.expression = expression;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		expression.collectFreeVariables(freeVariables);

		final Renderer filter = oliveBuilder.filter(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));
		filter.methodGen().visitCode();
		expression.render(filter);
		filter.methodGen().returnValue();
		filter.methodGen().visitMaxs(0, 0);
		filter.methodGen().visitEnd();
	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return defs.fail(expression.resolve(defs, errorHandler));
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = expression.typeCheck(errorHandler);
		if (ok) {
			if (!expression.type().isSame(Imyhat.BOOLEAN)) {
				errorHandler.accept(String.format("%d:%d: Expression is “Where” clause must be boolean, got %s.", line,
						column, expression.type().name()));
				return false;
			}
		}
		return ok;
	}

}
