package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodePick extends OliveClauseNode {

	private final int column;
	private final List<String> discriminators;
	private List<Target> discriminatorVariables;
	private final ExpressionNode extractor;
	private final int line;

	private final Boolean max;

	public OliveClauseNodePick(int line, int column, Boolean max, ExpressionNode extractor,
			List<String> discriminators) {
		this.line = line;
		this.column = column;
		this.max = max;
		this.extractor = extractor;
		this.discriminators = discriminators;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		extractor.collectFreeVariables(freeVariables);

		oliveBuilder.line(line);
		final Renderer extractorMethod = oliveBuilder.pick(extractor.type(), max, discriminatorVariables.stream(),
				oliveBuilder.loadableValues().filter(value -> freeVariables.contains(value.name()))
						.toArray(LoadableValue[]::new));
		extractorMethod.methodGen().visitCode();
		extractor.render(extractorMethod);
		extractorMethod.methodGen().box(extractor.type().asmType());
		extractorMethod.methodGen().returnValue();
		extractorMethod.methodGen().visitMaxs(0, 0);
		extractorMethod.methodGen().visitEnd();
	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Supplier<Stream<Constant>> constants,
			Consumer<String> errorHandler) {
		final Optional<List<Target>> maybeDiscriminatorVariables = OliveClauseNodeBaseBy.checkDiscriminators(line,
				column, defs, discriminators, errorHandler);
		maybeDiscriminatorVariables.ifPresent(x -> discriminatorVariables = x);
		return defs.fail(maybeDiscriminatorVariables.isPresent() & extractor.resolve(defs, errorHandler));
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		return extractor.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		if (!extractor.typeCheck(errorHandler)) {
			return false;
		}
		if (extractor.type().isSame(Imyhat.DATE) || extractor.type().isSame(Imyhat.INTEGER)) {
			return true;
		}
		errorHandler.accept(String.format("%d:%d: Expected date or integer for sorting but got %s.", line, column,
				extractor.type().name()));
		return false;
	}

}
