package ca.on.oicr.gsi.shesmu.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public class OliveClauseNodeJoin extends OliveClauseNode {

	private final int column;
	private final int line;
	private final String format;
	private InputFormatDefinition inputFormat;
	private List<Consumer<JoinBuilder>> joins = new ArrayList<>();

	public OliveClauseNodeJoin(int line, int column, String format) {
		super();
		this.line = line;
		this.column = column;
		this.format = format;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.TRANSFORMED : state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final JoinBuilder join = oliveBuilder.join(inputFormat.type());
		joins.forEach(a -> a.accept(join));
		join.finish();
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs,
			Supplier<Stream<Constant>> constants, Consumer<String> errorHandler) {
		inputFormat = definedFormats.apply(format);
		if (inputFormat == null) {
			errorHandler.accept(String.format("%d:%d: Unknown input format “%s” in Join.", line, column, format));
			return defs.fail(false);
		}

		Set<String> newNames = inputFormat.baseStreamVariables()//
				.map(Target::name)//
				.collect(Collectors.toSet());

		List<String> duplicates = defs.stream()//
				.filter(n -> n.flavour() == Flavour.STREAM && newNames.contains(n.name()))//
				.map(Target::name)//
				.sorted()//
				.collect(Collectors.toList());

		if (duplicates.isEmpty()) {
			defs.stream()//
					.filter(n -> n.flavour() == Flavour.STREAM)//
					.forEach(n -> joins.add(jb -> jb.add(n.type().asmType(), n.name(), true)));
			inputFormat.baseStreamVariables()
					.forEach(n -> joins.add(jb -> jb.add(n.type().asmType(), n.name(), false)));
		} else {
			errorHandler.accept(String.format(
					"%d:%d: Duplicate variables on both sides of Join. Please rename or drop the following using a Let: %s",
					line, column, String.join(", ", duplicates)));
			return defs.fail(false);
		}
		return defs.replaceStream(//
				Stream.concat(//
						defs.stream()//
								.filter(n -> n.flavour() == Flavour.STREAM), //
						inputFormat.baseStreamVariables()), //
				duplicates.isEmpty());
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return true;
	}

}
