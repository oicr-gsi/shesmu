package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import io.prometheus.client.Gauge;

public class OliveClauseNodeMonitor extends OliveClauseNode implements RejectNode {

	private static final Type A_CHILD_TYPE = Type.getType(Gauge.Child.class);
	private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method METHOD_CHILD__INC = new Method("inc", Type.VOID_TYPE, new Type[] {});
	private static final Method METHOD_GAUGE__LABELS = new Method("labels", Type.getType(Object.class),
			new Type[] { Type.getType(String[].class) });
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
	public void collectFreeVariables(Set<String> freeVariables) {
		labels.forEach(arg -> arg.collectFreeVariables(freeVariables, Flavour::needsCapture));
	}

	@Override
	public int column() {
		return column;
	}

	@Override
	public Stream<OliveClauseRow> dashboard() {
		return Stream.of(new OliveClauseRow("Monitor", line, column, false, false, labels.stream()//
				.map(label -> {
					final Set<String> inputs = new TreeSet<>();
					label.collectFreeVariables(inputs, Flavour::isStream);
					return new VariableInformation(metricName + "{" + label.name() + "}", Imyhat.STRING,
							inputs.stream(), Behaviour.DEFINITION);

				})));
	}

	@Override
	public ClauseStreamOrder ensureRoot(ClauseStreamOrder state, Set<String> signableNames,
			Consumer<String> errorHandler) {
		return state;
	}

	private List<String> labelNames() {
		return labels.stream().map(MonitorArgumentNode::name).collect(Collectors.toList());
	}

	@Override
	public int line() {
		return line;
	}

	private void render(Renderer renderer) {
		renderer.methodGen().push(labels.size());
		renderer.methodGen().newArray(A_STRING_TYPE);
		for (int i = 0; i < labels.size(); i++) {
			renderer.methodGen().dup();
			renderer.methodGen().push(i);
			labels.get(i).render(renderer);
			renderer.methodGen().arrayStore(A_STRING_TYPE);
		}
	}

	@Override
	public void render(RootBuilder builder, BaseOliveBuilder oliveBuilder,
			Map<String, OliveDefineBuilder> definitions) {
		final Set<String> freeVariables = new HashSet<>();
		labels.forEach(arg -> arg.collectFreeVariables(freeVariables, Flavour::needsCapture));

		oliveBuilder.line(line);
		final Renderer renderer = oliveBuilder.monitor(line, column, metricName, help, labelNames(), oliveBuilder
				.loadableValues().filter(value -> freeVariables.contains(value.name())).toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		render(renderer);
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
	}

	@Override
	public void render(RootBuilder builder, Renderer renderer) {
		builder.loadGauge(metricName, help, labelNames(), renderer.methodGen());
		render(renderer);
		renderer.methodGen().invokeVirtual(A_GAUGE_TYPE, METHOD_GAUGE__LABELS);
		renderer.methodGen().checkCast(A_CHILD_TYPE);
		renderer.methodGen().invokeVirtual(A_CHILD_TYPE, METHOD_CHILD__INC);
	}

	@Override
	public NameDefinitions resolve(InputFormatDefinition inputFormatDefinition,
			Function<String, InputFormatDefinition> definedFormats, NameDefinitions defs, ConstantRetriever constants,
			Consumer<String> errorHandler) {
		return defs.fail(labels.stream().filter(arg -> arg.resolve(defs, errorHandler)).count() == labels.size());
	}

	@Override
	public boolean resolveDefinitions(Map<String, OliveNodeDefinition> definedOlives,
			Function<String, FunctionDefinition> definedFunctions, Function<String, ActionDefinition> definedActions,
			Set<String> metricNames, Map<String, List<Imyhat>> dumpers, Consumer<String> errorHandler) {
		if (metricNames.contains(metricName)) {
			errorHandler.accept(String.format("%d:%d: Duplicated monitoring metric “%s”.", line, column, metricName));
			return false;
		}
		metricNames.add(metricName);
		if (help.trim().isEmpty()) {
			errorHandler.accept(String.format("%d:%d: Help text is required by Prometheus for monitoring metric “%s”.",
					line, column, metricName));
			return false;
		}

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
