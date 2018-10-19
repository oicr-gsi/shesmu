package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;

public final class OliveClauseNodeGroup extends OliveClauseNode {

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
			if (!target.map(Target::flavour).map(Flavour::isStream).orElse(false)) {
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

	private final List<GroupNode> children;
	protected final int column;
	private final List<DiscriminatorNode> discriminators;
	protected final int line;

	public OliveClauseNodeGroup(int line, int column, List<GroupNode> children,
			List<DiscriminatorNode> discriminators) {
		this.line = line;
		this.column = column;
		this.children = children;
		this.discriminators = discriminators;
	}

	@Override
	public OliveClauseRow dashboard() {
		return new OliveClauseRow("Group", line, column, true, true, //
				Stream.concat(//
						children.stream()//
								.map(child -> {
									final Set<String> inputs = new TreeSet<>();
									child.collectFreeVariables(inputs, Flavour::isStream);
									return new VariableInformation(child.name(), child.type(), inputs.stream(),
											Behaviour.DEFINITION);
								}), //
						discriminators.stream()//
								.map(DiscriminatorNode::dashboard)));
	}

	@Override
	public final ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler) {
		if (state == ClauseStreamOrder.PURE) {
			discriminators.stream()//
					.forEach(d -> d.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
			children.stream()//
					.forEach(c -> c.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
			return ClauseStreamOrder.TRANSFORMED;
		}
		return state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		children.stream().forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));

		oliveBuilder.line(line);
		final RegroupVariablesBuilder regroup = oliveBuilder.regroup(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));

		discriminators.forEach(d -> d.render(regroup));
		children.stream().forEach(group -> group.render(regroup, builder));
		regroup.finish();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}

	@Override
	public final NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		boolean ok = //
				children.stream()//
						.filter(child -> child.resolve(defs, defs, errorHandler))//
						.count() == children.size() & //
						discriminators.stream()//
								.filter(discriminator -> discriminator.resolve(defs, errorHandler))//
								.count() == discriminators.size();

		ok &= children.stream().noneMatch(group -> {
			final boolean isDuplicate = defs.get(group.name()).filter(variable -> !variable.flavour().isStream())
					.isPresent();
			if (isDuplicate) {
				errorHandler.accept(String.format("%d:%d: Redefinition of variable “%s”.", group.line(), group.column(),
						group.name()));
			}
			return isDuplicate;
		});
		return defs.replaceStream(Stream.concat(discriminators.stream(), children.stream()), ok);
	}

	@Override
	public final boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		boolean ok = children.stream()
				.filter(group -> group.resolveDefinitions(definedOlives, definedFunctions, definedActions,
						errorHandler))
				.count() == children.size()
				& discriminators.stream().filter(group -> group.resolveFunctions(definedFunctions, errorHandler))
						.count() == discriminators.size();

		ok &= Stream.<DefinedTarget>concat(discriminators.stream(), children.stream())//
				.collect(Collectors.groupingBy(DefinedTarget::name))//
				.entrySet().stream()//
				.filter(e -> e.getValue().size() > 1)//
				.peek(e -> errorHandler.accept(String.format("%d:%d: “Group” has duplicate name %s: %s", line, column,
						e.getKey(), e.getValue().stream()//
								.sorted(Comparator.comparingInt(DefinedTarget::line)
										.thenComparingInt(DefinedTarget::column))//
								.map(l -> String.format("%d:%d", l.line(), l.column()))//
								.collect(Collectors.joining(", ")))))//
				.count() == 0;
		return ok;
	}

	@Override
	public final boolean typeCheck(Consumer<String> errorHandler) {
		return children.stream().filter(group -> group.typeCheck(errorHandler)).count() == children.size();
	}

}
