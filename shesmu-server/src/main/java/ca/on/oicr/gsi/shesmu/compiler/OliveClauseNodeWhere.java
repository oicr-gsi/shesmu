package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;

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
	public int column() {
		return column;
	}

	@Override
	public OliveClauseRow dashboard() {
		final Set<String> inputs = new TreeSet<>();
		expression.collectFreeVariables(inputs, Flavour::isStream);
		return new OliveClauseRow("Where", line, column, true, false, inputs.stream()//
				.map(n -> new VariableInformation(n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER)));
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler) {
		if (state == ClauseStreamOrder.PURE) {
			expression.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
		}
		return state;
	}

	@Override
	public int line() {
		return line;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		expression.collectFreeVariables(freeVariables, Flavour::needsCapture);

		final Renderer filter = oliveBuilder.filter(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));
		filter.methodGen().visitCode();
		expression.render(filter);
		filter.methodGen().returnValue();
		filter.methodGen().visitMaxs(0, 0);
		filter.methodGen().visitEnd();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs,
			Supplier<Stream<ConstantDefinition>> constants, Consumer<String> errorHandler) {
		return defs.fail(expression.resolve(defs, errorHandler));
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
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
