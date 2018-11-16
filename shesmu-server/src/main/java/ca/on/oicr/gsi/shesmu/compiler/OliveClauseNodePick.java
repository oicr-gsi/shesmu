package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;

public class OliveClauseNodePick extends OliveClauseNode {

	private final int column;
	private final List<String> discriminators;
	private List<Target> discriminatorVariables;
	private final ExpressionNode extractor;
	private final int line;

	private final boolean max;

	public OliveClauseNodePick(int line, int column, boolean max, ExpressionNode extractor,
			List<String> discriminators) {
		this.line = line;
		this.column = column;
		this.max = max;
		this.extractor = extractor;
		this.discriminators = discriminators;
	}

	@Override
	public int column() {
		return column;
	}

	@Override
	public OliveClauseRow dashboard() {
		final Set<String> inputs = new TreeSet<>();
		extractor.collectFreeVariables(inputs, Flavour::isStream);
		return new OliveClauseRow("Pick " + (max ? "Max" : "Min"), line, column, true, false, //
				Stream.concat(//
						inputs.stream()//
								.map(n -> new VariableInformation(n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER)), //
						discriminatorVariables.stream()//
								.map(discriminator -> new VariableInformation(discriminator.name(),
										discriminator.type(), Stream.of(discriminator.name()),
										Behaviour.PASSTHROUGH))));
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler) {
		if (state == ClauseStreamOrder.PURE) {
			discriminatorVariables.stream()//
					.filter(v -> v.flavour() == Flavour.STREAM_SIGNABLE)//
					.map(Target::name)//
					.forEach(signableNames::add);
			extractor.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
		}
		return state;
	}

	@Override
	public int line() {
		return line;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		extractor.collectFreeVariables(freeVariables, Flavour::needsCapture);

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

		oliveBuilder.measureFlow(builder.sourcePath(), line, column);
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs, ConstantRetriever constants,
			Consumer<String> errorHandler) {
		final Optional<List<Target>> maybeDiscriminatorVariables = OliveClauseNodeGroup.checkDiscriminators(line,
				column, defs, discriminators, errorHandler);
		maybeDiscriminatorVariables.ifPresent(x -> discriminatorVariables = x);
		return defs.fail(maybeDiscriminatorVariables.isPresent() & extractor.resolve(defs, errorHandler));
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		return extractor.resolveFunctions(definedFunctions, errorHandler);
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
