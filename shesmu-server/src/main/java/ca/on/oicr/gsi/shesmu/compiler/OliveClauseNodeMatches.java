package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodeMatches extends OliveClauseNode {

	private final List<ExpressionNode> arguments;
	private final int column;
	private final int line;
	private final String name;
	private OliveNodeDefinition target;

	public OliveClauseNodeMatches(int line, int column, String name, List<ExpressionNode> arguments) {
		this.line = line;
		this.column = column;
		this.name = name;
		this.arguments = arguments;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		switch (state) {
		case BAD:
			return ClauseStreamOrder.BAD;
		case TRANSFORMED:
			errorHandler.accept(
					String.format("%d:%d: “Matches” clause cannot be applied to grouped result.", line, column));
			return ClauseStreamOrder.BAD;

		case PURE:
			return target.isRoot() ? ClauseStreamOrder.PURE : ClauseStreamOrder.TRANSFORMED;
		default:
			return ClauseStreamOrder.BAD;
		}
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		oliveBuilder.line(line);
		oliveBuilder.matches(definitions.get(name),
				arguments.stream().map(argument -> renderer -> argument.render(renderer)));

	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Supplier<Stream<Constant>> constants,
			Consumer<String> errorHandler) {
		final NameDefinitions limitedDefs = defs.replaceStream(Stream.empty(), true);
		boolean good = arguments.stream().filter(argument -> argument.resolve(limitedDefs, errorHandler))
				.count() == arguments.size();
		final Optional<Stream<Target>> replacements = target.outputStreamVariables(errorHandler, constants);
		good &= replacements.isPresent();
		return defs.replaceStream(replacements.orElseGet(Stream::empty), good);

	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		final boolean ok = arguments.stream()
				.filter(argument -> argument.resolveFunctions(definedFunctions, errorHandler))
				.count() == arguments.size();
		if (definedOlives.containsKey(name)) {
			target = definedOlives.get(name);
			if (target.parameterCount() != arguments.size()) {
				errorHandler.accept(String.format(
						"%d:%d: “Define %s” specifies %d parameters, but “Matches” has only %d arguments.", line,
						column, name, target.parameterCount(), arguments.size()));
				return false;
			}
			return ok;
		}
		errorHandler.accept(String.format("%d:%d: Cannot find “Define %s” for “Matches”.", line, column, name));
		return false;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return IntStream.range(0, arguments.size()).filter(index -> {
			final boolean isSame = arguments.get(index).type().isSame(target.parameterType(index));
			if (!isSame) {
				errorHandler.accept(String.format("%d:%d: Parameter %d to “Matches %s” expects %s, but got %s.", line,
						column, name, index, target.parameterType(index).name(), arguments.get(index).type().name()));

			}
			return isSame;
		}).count() == arguments.size();
	}
}
