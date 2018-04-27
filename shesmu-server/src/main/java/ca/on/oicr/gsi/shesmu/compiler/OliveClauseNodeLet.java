package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodeLet extends OliveClauseNode {

	private final List<LetArgumentNode> arguments;

	public OliveClauseNodeLet(List<LetArgumentNode> arguments) {
		super();
		this.arguments = arguments;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.TRANSFORMED : state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		arguments.forEach(argument -> argument.collectFreeVariables(freeVariables));
		final LetBuilder let = oliveBuilder.let(oliveBuilder.loadableValues()
				.filter(loadable -> freeVariables.contains(loadable.name())).toArray(LoadableValue[]::new));
		arguments.forEach(argument -> argument.render(let));
		let.finish();
	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Supplier<Stream<Constant>> constants,
			Consumer<String> errorHandler) {
		final boolean good = arguments.stream().filter(argument -> argument.resolve(defs, errorHandler))
				.count() == arguments.size();
		return defs.replaceStream(arguments.stream().map(x -> x), good);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, LookupDefinition> definedLookups, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		return arguments.stream().filter(argument -> argument.resolveLookups(definedLookups, errorHandler))
				.count() == arguments.size();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count() == arguments.size();
	}

}
