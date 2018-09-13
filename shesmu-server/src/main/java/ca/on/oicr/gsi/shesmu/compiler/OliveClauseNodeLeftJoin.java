package ca.on.oicr.gsi.shesmu.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public final class OliveClauseNodeLeftJoin extends OliveClauseNode {

	private final List<GroupNode> children;
	protected final int column;
	private final String format;
	private InputFormatDefinition inputFormat;
	protected final int line;
	private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
	private List<Target> discriminators;

	public OliveClauseNodeLeftJoin(int line, int column, String format, List<GroupNode> children) {
		this.line = line;
		this.column = column;
		this.format = format;
		this.children = children;
	}

	@Override
	public final ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler) {
		if (state == ClauseStreamOrder.PURE) {
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
		final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin = oliveBuilder.leftJoin(inputFormat.type(),
				oliveBuilder.loadableValues().filter(value -> freeVariables.contains(value.name()))
						.toArray(LoadableValue[]::new));
		joins.forEach(a -> a.accept(leftJoin.first()));
		leftJoin.first().finish();

		discriminators.forEach(discriminator -> {
			leftJoin.second().addKey(discriminator.type().asmType(), discriminator.name(), context -> {
				context.loadStream();
				context.methodGen().invokeVirtual(context.streamType(),
						new Method(discriminator.name(), discriminator.type().asmType(), new Type[] {}));
			});
		});
		children.stream().forEach(group -> group.render(leftJoin.second(), builder));
		leftJoin.second().finish();

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}

	@Override
	public final NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		inputFormat = definedFormats.apply(format);
		if (inputFormat == null) {
			errorHandler.accept(String.format("%d:%d: Unknown input format “%s” in LeftJoin.", line, column, format));
			return defs.fail(false);
		}

		final Set<String> newNames = inputFormat.baseStreamVariables()//
				.map(Target::name)//
				.collect(Collectors.toSet());

		final List<String> duplicates = defs.stream()//
				.filter(n -> n.flavour().isStream() && newNames.contains(n.name()))//
				.map(Target::name)//
				.sorted()//
				.collect(Collectors.toList());

		if (duplicates.isEmpty()) {
			defs.stream()//
					.filter(n -> n.flavour().isStream())//
					.forEach(n -> joins.add(jb -> jb.add(n.type().asmType(), n.name(), true)));
			inputFormat.baseStreamVariables()
					.forEach(n -> joins.add(jb -> jb.add(n.type().asmType(), n.name(), false)));
		} else {
			errorHandler.accept(String.format(
					"%d:%d: Duplicate variables on both sides of LeftJoin. Please rename or drop the following using a Let: %s",
					line, column, String.join(", ", duplicates)));
			return defs.fail(false);
		}

		discriminators = defs.stream().filter(t -> t.flavour().isStream() && t.flavour() != Flavour.STREAM_SIGNATURE)
				.collect(Collectors.toList());

		NameDefinitions joinedDefs = defs.replaceStream(//
				Stream.concat(//
						discriminators.stream(), //
						inputFormat.baseStreamVariables()//
								.map(x -> new Target() { // We remove any signing from the joined input

									@Override
									public Imyhat type() {
										return x.type();
									}

									@Override
									public String name() {
										return x.name();
									}

									@Override
									public Flavour flavour() {
										return Flavour.STREAM;
									}
								})), //
				true);

		boolean ok = children.stream().filter(group -> {
			final boolean isDuplicate = discriminators.stream().anyMatch(t -> t.name().equals(group.name()));
			if (isDuplicate) {
				errorHandler.accept(String.format("%d:%d: Redefinition of variable “%s”.", group.line(), group.column(),
						group.name()));
			}
			return group.resolve(joinedDefs, errorHandler) && !isDuplicate;
		}).count() == children.size();

		return defs.replaceStream(Stream.concat(discriminators.stream(), children.stream()), ok);
	}

	@Override
	public final boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		boolean ok = children.stream().filter(
				group -> group.resolveDefinitions(definedOlives, definedFunctions, definedActions, errorHandler))
				.count() == children.size();
		if (children.stream().map(GroupNode::name).distinct().count() != children.size()) {
			ok = false;
			errorHandler.accept(String.format(
					"%d:%d: Duplicate collected variables in “LeftJoin” clause. Should be: %s", line, column,
					children.stream().map(GroupNode::name).sorted().distinct().collect(Collectors.joining(", "))));
		}
		return ok;
	}

	@Override
	public final boolean typeCheck(Consumer<String> errorHandler) {
		return children.stream().filter(group -> group.typeCheck(errorHandler)).count() == children.size();
	}

}
