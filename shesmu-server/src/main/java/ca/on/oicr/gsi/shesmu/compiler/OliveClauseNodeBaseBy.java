package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public abstract class OliveClauseNodeBaseBy<T extends ByChildNode> extends OliveClauseNode {

	/**
	 * Check that the list of strings provided are valid discriminators
	 *
	 * That is, they are defined stream variables
	 */
	public static Optional<List<Target>> checkDiscriminators(int line, int column, NameDefinitions defs,
			List<String> discriminators, Consumer<String> errorHandler) {
		final List<Target> discriminatorVariables = discriminators.stream().map(name -> {
			final Optional<Target> target = defs.get(name);
			if (!target.isPresent()) {
				errorHandler.accept(String.format("%d:%d: Undefined variable “%s” in “By”.", line, column, name));
				return null;
			}
			if (!target.map(Target::flavour).map(Flavour.STREAM::equals).orElse(false)) {
				errorHandler.accept(String.format("%d:%d: Non-stream variable “%s” in “By”.", line, column, name));
				return null;
			}
			return target.orElse(null);
		}).filter(Objects::nonNull).collect(Collectors.toList());

		if (discriminators.size() != discriminatorVariables.size()) {
			return Optional.empty();
		}
		return Optional.of(discriminatorVariables);
	}

	private final List<T> children;
	private final int column;
	private final List<String> discriminators;
	private List<Target> discriminatorVariables = Collections.emptyList();
	protected final int line;

	private final String syntax;

	public OliveClauseNodeBaseBy(String syntax, int line, int column, List<T> children, List<String> discriminators) {
		this.syntax = syntax;
		this.line = line;
		this.column = column;
		this.children = children;
		this.discriminators = discriminators;
	}

	protected final Stream<T> children() {
		return children.stream();
	}

	protected final Stream<Target> discriminators() {
		return discriminatorVariables.stream();
	}

	@Override
	public final ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.TRANSFORMED : state;
	}

	@Override
	public final NameDefinitions resolve(InputFormatDefinition inputFormatDefinition, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		boolean ok = children.stream().filter(child -> child.resolve(defs, errorHandler)).count() == children.size();
		final Optional<List<Target>> maybeDiscriminatorVariables = checkDiscriminators(line, column, defs,
				discriminators, errorHandler);
		maybeDiscriminatorVariables.ifPresent(x -> discriminatorVariables = x);

		if (!maybeDiscriminatorVariables.isPresent()) {
			ok = false;
		}

		ok &= children.stream().noneMatch(group -> {
			final boolean isDuplicate = defs.get(group.name()).filter(variable -> variable.flavour() != Flavour.STREAM)
					.isPresent();
			if (isDuplicate) {
				errorHandler.accept(String.format("%d:%d: Redefinition of variable “%s”.", group.line(), group.column(),
						group.name()));
			}
			return isDuplicate;
		});
		return defs.replaceStream(Stream.concat(discriminatorVariables.stream(), children.stream()), ok);
	}

	@Override
	public final boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		boolean ok = children.stream().filter(
				group -> group.resolveDefinitions(definedOlives, definedFunctions, definedActions, errorHandler))
				.count() == children.size();
		if (discriminators.stream().distinct().count() != discriminators.size()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Duplicate “By” variables in “%s” clause. Should be: %s", line,
					column, syntax, discriminators.stream().sorted().distinct().collect(Collectors.joining(", "))));
		}
		if (children.stream().map(ByChildNode::name).distinct().count() != children.size()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Duplicate collected variables in “Group” clause. Should be: %s",
					line, column,
					children.stream().map(ByChildNode::name).sorted().distinct().collect(Collectors.joining(", "))));
		}
		final List<T> badGroups = children.stream().filter(child -> discriminators.contains(child.name()))
				.collect(Collectors.toList());
		if (!badGroups.isEmpty()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Collected variables have same names as “By” variables: %s", line,
					column, badGroups.stream().map(ByChildNode::name).collect(Collectors.joining(", "))));
		}
		return ok;
	}

	@Override
	public final boolean typeCheck(Consumer<String> errorHandler) {
		return children.stream().filter(group -> group.typeCheck(errorHandler)).count() == children.size();
	}

}
