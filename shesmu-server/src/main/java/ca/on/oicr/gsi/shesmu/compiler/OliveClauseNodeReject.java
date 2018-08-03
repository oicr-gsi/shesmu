package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodeReject extends OliveClauseNode {

	private final int column;
	private final ExpressionNode expression;
	private final List<RejectNode> handlers;
	private final int line;

	public OliveClauseNodeReject(int line, int column, ExpressionNode expression, List<RejectNode> handlers) {
		super();
		this.line = line;
		this.column = column;
		this.expression = expression;
		this.handlers = handlers;
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
		handlers.forEach(handler -> handler.collectFreeVariables(freeVariables));
		final Renderer renderer = oliveBuilder.filter(oliveBuilder.loadableValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));

		renderer.methodGen().visitCode();
		expression.render(renderer);
		final Label handleReject = renderer.methodGen().newLabel();
		renderer.methodGen().ifZCmp(GeneratorAdapter.NE, handleReject);
		renderer.methodGen().push(true);
		renderer.methodGen().returnValue();
		renderer.methodGen().mark(handleReject);
		handlers.forEach(handler -> handler.render(builder, renderer));
		renderer.methodGen().push(false);
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		return defs.fail(expression.resolve(defs, errorHandler) & handlers.stream()
				.filter(handler -> handler.resolve(inputFormatDefinition, defs, constants, errorHandler).isGood())
				.count() == handlers.size());
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler)
				& handlers.stream().filter(handler -> handler.resolveDefinitions(definedOlives, definedFunctions,
						definedActions, metricNames, dumpers, errorHandler)).count() == handlers.size();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean success = expression.typeCheck(errorHandler);
		if (success) {
			if (!expression.type().isSame(Imyhat.BOOLEAN)) {
				success = false;
				expression.typeError(Imyhat.BOOLEAN.name(), expression.type(), errorHandler);
			}
		}
		return success
				&& handlers.stream().filter(handler -> handler.typeCheck(errorHandler)).count() == handlers.size();
	}

}
