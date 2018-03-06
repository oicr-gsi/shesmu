package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public class OliveClauseNodeGroup extends OliveClauseNode {

	private class Repackaged extends Target {
		private final Target original;

		public Repackaged(Target original) {
			super();
			this.original = original;
		}

		@Override
		public Flavour flavour() {
			return original.flavour();
		}

		@Override
		public String name() {
			return original.name();
		}

		@Override
		public Imyhat type() {
			return original.type();
		}

	}

	private final int column;
	private final List<String> discriminators;
	private List<Target> discriminatorVariables;
	private final List<GroupNode> groups;
	private final int line;

	private List<Target> newStreamVars;

	public OliveClauseNodeGroup(int line, int column, List<GroupNode> groups, List<String> discriminators) {
		this.line = line;
		this.column = column;
		this.groups = groups;
		this.discriminators = discriminators;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.GROUPED : state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		groups.stream().map(GroupNode::expression)
				.forEach(expression -> expression.collectFreeVariables(freeVariables));

		final RegroupVariablesBuilder regroup = oliveBuilder.group(oliveBuilder.loadableValues()
				.filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));

		discriminatorVariables.forEach(discriminator -> {
			regroup.addKey(discriminator.type().asmType(), discriminator.name(), context -> {
				context.loadStream();
				context.methodGen().invokeVirtual(context.streamType(),
						new Method(discriminator.name(), discriminator.type().asmType(), new Type[] {}));
			});
		});
		groups.forEach(group -> group.render(regroup, builder));
		regroup.finish();
	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		boolean ok = groups.stream().filter(group -> group.resolve(defs, errorHandler)).count() == groups.size();
		discriminatorVariables = discriminators.stream().map(name -> {
			final Optional<Target> target = defs.get(name);
			if (!target.isPresent()) {
				errorHandler.accept(String.format("%d:%d: Undefined variable “%s” in “By”.", line, column, name));
			}
			if (!target.map(Target::flavour).map(Flavour.STREAM::equals).orElse(false)) {
				errorHandler.accept(String.format("%d:%d: Non-stream variable “%s” in “By”.", line, column, name));
				return null;
			}
			return target.orElse(null);
		}).filter(Objects::nonNull).collect(Collectors.toList());

		if (discriminators.size() != discriminatorVariables.size()) {
			ok = false;
		}

		ok &= groups.stream().noneMatch(group -> {
			final boolean isDuplicate = defs.get(group.name()).filter(variable -> variable.flavour() != Flavour.STREAM)
					.isPresent();
			if (isDuplicate) {
				errorHandler.accept(String.format("%d:%d: Redefinition of variable “%s”.", group.line(), group.column(),
						group.name()));
			}
			return isDuplicate;
		});

		newStreamVars = Stream.concat(discriminatorVariables.stream().map(Repackaged::new), groups.stream())
				.collect(Collectors.toList());
		return defs.replaceStream(newStreamVars.stream(), ok);
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, Lookup> definedLookups, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		boolean ok = groups.stream()
				.filter(group -> group.resolveDefinitions(definedOlives, definedLookups, definedActions, errorHandler))
				.count() == groups.size();
		if (discriminators.stream().distinct().count() != discriminators.size()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Duplicate “By” variables in “Group” clause. Should be: %s", line,
					column, discriminators.stream().sorted().distinct().collect(Collectors.joining(", "))));
		}
		if (groups.stream().map(GroupNode::name).distinct().count() != groups.size()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Duplicate collected variables in “Group” clause. Should be: %s",
					line, column,
					groups.stream().map(GroupNode::name).sorted().distinct().collect(Collectors.joining(", "))));
		}
		final List<GroupNode> badGroups = groups.stream().filter(group -> discriminators.contains(group.name()))
				.collect(Collectors.toList());
		if (!badGroups.isEmpty()) {
			ok = false;
			errorHandler.accept(String.format("%d:%d: Collected variables have same names as “By” variables: %s", line,
					column, badGroups.stream().map(GroupNode::name).collect(Collectors.joining(", "))));
		}
		return ok;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return groups.stream().filter(group -> group.typeCheck(errorHandler)).count() == groups.size();
	}

}
