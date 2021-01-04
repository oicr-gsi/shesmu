package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.PartitionCount;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;

public class CollectNodePartitionCount extends CollectNode {

  private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
  private static final Type A_PARTITION_COUNT_TYPE = Type.getType(PartitionCount.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private final ExpressionNode expression;
  private List<String> definedNames;

  public CollectNodePartitionCount(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    expression.collectFreeVariables(names, predicate);
    names.removeAll(remove);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Renderer renderer =
        builder.map(
            line(),
            column(),
            name,
            Imyhat.BOOLEAN,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    expression.render(renderer);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
    builder.collector(
        A_TUPLE_TYPE,
        r -> {
          r.methodGen().getStatic(A_PARTITION_COUNT_TYPE, "COLLECTOR", A_COLLECTOR_TYPE);
        });
  }

  @Override
  public String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    return String.format(
        "$runtime.partitionCount(%s, %s)",
        builder.finish(),
        builder
            .renderer()
            .lambda(
                1,
                (r, args) -> {
                  name.create(rr -> args.apply(0)).forEach(r::define);
                  return expression.renderEcma(r);
                }));
  }

  @Override
  public boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final boolean ok = expression.resolve(defs.bind(name), errorHandler);
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return PartitionCount.TYPE;
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (!expression.type().isSame(Imyhat.BOOLEAN)) {
        expression.typeError(Imyhat.BOOLEAN, expression.type(), errorHandler);
        ok = false;
      }
    }
    return ok;
  }
}
