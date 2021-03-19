package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class OliveClauseNodeMonitor extends OliveClauseNode implements RejectNode {

  private static final Type A_CHILD_TYPE = Type.getType(Gauge.Child.class);
  private static final Type A_GAUGE_TYPE = Type.getType(Gauge.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method METHOD_CHILD__INC = new Method("inc", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_GAUGE__LABELS =
      new Method("labels", Type.getType(Object.class), new Type[] {Type.getType(String[].class)});

  private final int column;
  private final String help;
  private final Optional<String> label;
  private final List<MonitorArgumentNode> labels;
  private final int line;
  private final String metricName;

  public OliveClauseNodeMonitor(
      Optional<String> label,
      int line,
      int column,
      String metricName,
      String help,
      List<MonitorArgumentNode> labels) {
    this.label = label;
    this.line = line;
    this.column = column;
    this.metricName = metricName;
    this.help = help;
    this.labels = labels;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    labels.forEach(arg -> arg.collectFreeVariables(freeVariables, Flavour::needsCapture));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    labels.forEach(label -> label.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Monitor"),
            line,
            column,
            false,
            false,
            labels.stream()
                .flatMap(
                    label -> {
                      final Set<String> inputs = new TreeSet<>();
                      label.collectFreeVariables(inputs, Flavour::isStream);
                      return label
                          .target()
                          .map(
                              t ->
                                  new VariableInformation(
                                      metricName + "{" + t.name() + "}",
                                      Imyhat.STRING,
                                      inputs.stream(),
                                      Behaviour.DEFINITION));
                    })));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    return state;
  }

  private List<String> labelNames() {
    return labels.stream()
        .flatMap(MonitorArgumentNode::target)
        .map(Target::name)
        .collect(Collectors.toList());
  }

  @Override
  public int line() {
    return line;
  }

  private void render(Renderer renderer) {
    renderer.methodGen().push((int) labels.stream().flatMap(MonitorArgumentNode::target).count());
    renderer.methodGen().newArray(A_STRING_TYPE);
    labels.stream()
        .flatMap(MonitorArgumentNode::target)
        .forEach(
            new Consumer<Target>() {
              private int index;

              @Override
              public void accept(Target target) {
                final var i = index++;
                renderer.methodGen().dup();
                renderer.methodGen().push(i);
                labels.get(i).render(renderer);
                renderer.methodGen().arrayStore(A_STRING_TYPE);
              }
            });
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    labels.forEach(arg -> arg.collectFreeVariables(freeVariables, Flavour::needsCapture));

    oliveBuilder.line(line);
    final var renderer =
        oliveBuilder.monitor(
            line,
            column,
            metricName,
            help,
            labelNames(),
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
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
  public Stream<LoadableValue> requiredCaptures(RootBuilder builder) {
    return Stream.empty();
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    return defs.fail(
        labels.stream().filter(arg -> arg.resolve(defs, errorHandler)).count() == labels.size());
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    if (oliveCompilerServices.addMetric(metricName)) {
      errorHandler.accept(
          String.format("%d:%d: Duplicated monitoring metric “%s”.", line, column, metricName));
      return false;
    }
    if (help.trim().isEmpty()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Help text is required by Prometheus for monitoring metric “%s”.",
              line, column, metricName));
      return false;
    }

    final var labelNames =
        labels.stream()
            .flatMap(MonitorArgumentNode::target)
            .collect(Collectors.groupingBy(Target::name, Collectors.counting()));
    var ok = true;
    for (final var labelName : labelNames.entrySet()) {
      if (labelName.getValue() == 1) {
        continue;
      }
      errorHandler.accept(
          String.format("%d:%d: Duplicated label: %s", line, column, labelName.getKey()));
      ok = false;
    }
    return ok
        && labels.stream()
                .filter(n -> n.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == labels.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return labels.stream()
            .filter(arg -> arg.typeCheck(errorHandler) && arg.ensureType(errorHandler))
            .count()
        == labels.size();
  }
}
