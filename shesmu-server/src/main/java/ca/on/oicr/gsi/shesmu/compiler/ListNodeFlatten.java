package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListNodeFlatten extends ListNode {

  private final DestructuredArgumentNode childName;
  private List<String> definedNames;

  private Ordering ordering;

  private final SourceNode source;

  private final List<ListNode> transforms;

  private Imyhat type;

  public ListNodeFlatten(
      int line,
      int column,
      DestructuredArgumentNode childName,
      SourceNode source,
      List<ListNode> transforms) {
    super(line, column);
    ordering = source.ordering();
    this.childName = childName;
    this.source = source;
    this.transforms = transforms;
    childName.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final List<String> remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toList());
    source.collectFreeVariables(names, predicate);
    names.removeAll(remove);
    transforms.forEach(t -> t.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    source.collectPlugins(pluginFileNames);
    transforms.forEach(t -> t.collectPlugins(pluginFileNames));
  }

  @Override
  public final Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.BAD || ordering == Ordering.BAD) {
      return Ordering.BAD;
    }
    if (previous == Ordering.REQESTED && ordering == Ordering.REQESTED) {
      return Ordering.REQESTED;
    }
    return Ordering.RANDOM;
  }

  @Override
  public LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    final Set<String> freeVariables = new HashSet<>();
    collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Renderer renderer =
        builder.flatten(
            line(),
            column(),
            name,
            type,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    final JavaStreamBuilder flattenBuilder = source.render(renderer);
    final LoadableConstructor outputName =
        transforms
            .stream()
            .reduce(
                childName::render,
                (n, t) -> t.render(flattenBuilder, n),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    flattenBuilder.finish();
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
    return outputName;
  }

  @Override
  public Optional<List<Target>> resolve(
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler) {
    definedNames = name.stream().map(Target::name).collect(Collectors.toList());
    final NameDefinitions innerDefs = defs.bind(name);
    if (!source.resolve(innerDefs, errorHandler)) {
      return Optional.empty();
    }
    final Optional<List<Target>> nextName =
        transforms
            .stream()
            .reduce(
                Optional.of(childName.targets().collect(Collectors.toList())),
                (n, t) -> n.flatMap(innerName -> t.resolve(innerName, innerDefs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    nextName.ifPresent(n -> {});
    return nextName;
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return source.resolveFunctions(definedFunctions, errorHandler)
        & transforms
                .stream()
                .filter(t -> t.resolveFunctions(definedFunctions, errorHandler))
                .count()
            == transforms.size();
  }

  @Override
  public Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler)
        || !childName.typeCheck(source.streamType(), errorHandler)) {
      return Optional.empty();
    }
    ordering =
        transforms
            .stream()
            .reduce(
                ordering,
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final Optional<Imyhat> resultType =
        transforms
            .stream()
            .reduce(
                Optional.of(source.streamType()),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    resultType.ifPresent(t -> type = t);
    return resultType;
  }
}
