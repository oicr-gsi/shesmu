package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;

public final class OliveNodeRun extends OliveNode {

	private final String actionName;
	private final List<OliveArgumentNode> arguments;
	private final int column;
	private ActionDefinition definition;
	private final int line;

	public OliveNodeRun(int line, int column, String actionName, List<OliveArgumentNode> arguments,
			List<OliveClauseNode> clauses) {
		super(clauses);
		this.line = line;
		this.column = column;
		this.actionName = actionName;
		this.arguments = arguments;
	}

	@Override
	protected void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		// Do nothing.
	}

	@Override
	protected boolean collectDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
		final OliveBuilder oliveBuilder = builder.buildRunOlive();
		clauses().forEach(clause -> clause.render(builder, oliveBuilder, definitions));
		final Renderer action = oliveBuilder.finish();
		action.methodGen().visitCode();
		definition.initialize(action.methodGen());
		final int local = action.methodGen().newLocal(definition.type());
		action.methodGen().storeLocal(local);

		final Map<String, OliveArgumentNode> argumentMap = arguments.stream()
				.collect(Collectors.toMap(OliveArgumentNode::name, Function.identity()));
		definition.parameters().forEach(parameter -> {
			parameter.store(action, local, argumentMap.get(parameter.name())::render);
		});
		oliveBuilder.emitAction(action.methodGen(), local);
		action.methodGen().visitInsn(Opcodes.RETURN);
		action.methodGen().visitMaxs(0, 0);
		action.methodGen().visitEnd();
	}

	@Override
	public boolean resolve(Consumer<String> errorHandler) {
		final NameDefinitions defs = clauses().stream().reduce(NameDefinitions.root(Stream.empty()),
				(d, clause) -> clause.resolve(d, errorHandler), (a, b) -> {
					throw new UnsupportedOperationException();
				});
		return defs.isGood() & arguments.stream().filter(argument -> argument.resolve(defs, errorHandler))
				.count() == arguments.size();
	}

	@Override
	protected boolean resolveDefinitionsExtra(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Consumer<String> errorHandler) {
		boolean ok = arguments.stream().filter(arg -> arg.resolveLookups(definedLookups, errorHandler))
				.count() == arguments.size();

		final Set<String> argumentNames = arguments.stream().map(OliveArgumentNode::name).distinct()
				.collect(Collectors.toSet());
		if (argumentNames.size() != arguments.size()) {
			errorHandler.accept(String.format("%d:%d: Duplicate arguments to action.", line, column));
			ok = false;
		}

		definition = definedActions.apply(actionName);
		if (definition != null) {

			final Set<String> definedArgumentNames = definition.parameters().map(ParameterDefinition::name)
					.collect(Collectors.toSet());
			if (!definedArgumentNames.containsAll(argumentNames)) {
				ok = false;
				final Set<String> badTerms = new HashSet<>(argumentNames);
				badTerms.removeAll(definedArgumentNames);
				errorHandler.accept(String.format("%d:%d: Extra arguments for action %s: %s", line, column, actionName,
						String.join(", ", badTerms)));
			}
			if (!argumentNames.containsAll(definedArgumentNames)) {
				ok = false;
				final Set<String> badTerms = new HashSet<>(definedArgumentNames);
				badTerms.removeAll(argumentNames);
				errorHandler.accept(String.format("%d:%d: Missing arguments for action %s: %s", line, column,
						actionName, String.join(", ", badTerms)));
			}
		} else {
			errorHandler.accept(String.format("%d:%d: Unknown action for “%s”.", line, column, actionName));
			ok = false;
		}
		return ok;

	}

	@Override
	protected boolean typeCheckExtra(Consumer<String> errorHandler) {
		boolean ok = arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count() == arguments
				.size();
		if (ok) {
			final Map<String, Imyhat> argumentTypes = definition.parameters()
					.collect(Collectors.toMap(ParameterDefinition::name, ParameterDefinition::type));
			ok = arguments.stream()
					.filter(argument -> argument.ensureType(argumentTypes.get(argument.name()), errorHandler))
					.count() == arguments.size();
		}
		return ok;
	}
}
