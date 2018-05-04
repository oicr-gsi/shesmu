package ca.on.oicr.gsi.shesmu.compiler;

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

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Constant;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;

public class OliveClauseNodeMonitor extends OliveClauseNode {

	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private final int column;
	private final String help;
	private final List<MonitorArgumentNode> labels;
	private final int line;
	private final String metricName;

	public OliveClauseNodeMonitor(int line, int column, String metricName, String help,
			List<MonitorArgumentNode> labels) {
		this.line = line;
		this.column = column;
		this.metricName = metricName;
		this.help = help;
		this.labels = labels;
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Consumer<String> errorHandler) {
		return state;
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		labels.forEach(arg -> arg.collectFreeVariables(freeVariables));

		oliveBuilder.line(line);
		final Renderer renderer = oliveBuilder.monitor(metricName, help,
				labels.stream().map(MonitorArgumentNode::name).collect(Collectors.toList()),
				oliveBuilder.loadableValues().filter(value -> freeVariables.contains(value.name()))
						.toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		renderer.methodGen().push(labels.size());
		renderer.methodGen().newArray(A_STRING_TYPE);
		for (int i = 0; i < labels.size(); i++) {
			renderer.methodGen().dup();
			renderer.methodGen().push(i);
			labels.get(i).render(renderer);
			renderer.methodGen().arrayStore(A_STRING_TYPE);
		}
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
	}

	@Override
	public NameDefinitions resolve(NameDefinitions defs, Supplier<Stream<Constant>> constants,
			Consumer<String> errorHandler) {
		return defs.fail(labels.stream().filter(arg -> arg.resolve(defs, errorHandler)).count() == labels.size());
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Consumer<String> errorHandler) {
		if (metricNames.contains(metricName)) {
			errorHandler.accept(String.format("%d:%d: Duplicated monitoring metric “%s”.", line, column, metricName));
			return false;
		}
		metricNames.add(metricName);

		if (labels.stream().map(MonitorArgumentNode::name).distinct().count() != labels.size()) {
			errorHandler.accept(String.format("%d:%d: Duplicated labels.", line, column));
			return false;
		}

		return true;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return labels.stream().filter(arg -> arg.ensureType(errorHandler)).count() == labels.size();
	}

}
