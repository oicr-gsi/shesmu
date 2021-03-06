package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ListNodeFlatten extends ListNode {

  class FlattenLambda implements EcmaScriptRenderer.LambdaRender {

    private EcmaLoadableConstructor name = childName::renderEcma;
    private final EcmaLoadableConstructor outerName;

    public FlattenLambda(EcmaLoadableConstructor outerName) {
      this.outerName = outerName;
    }

    @Override
    public String render(EcmaScriptRenderer renderer, IntFunction<String> arg) {
      outerName.create(arg.apply(0)).forEach(renderer::define);
      final var builder = source.render(renderer);
      for (final var node : transforms) {
        name = node.render(builder, name);
      }
      return builder.finish();
    }
  }

  private final DestructuredArgumentNode childName;
  private List<String> definedNames;
  private Ordering ordering = Ordering.BAD;
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
    this.childName = childName;
    this.source = source;
    this.transforms = transforms;
    childName.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove =
        definedNames.stream().filter(name -> !names.contains(name)).collect(Collectors.toSet());
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
    final var renderer =
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
    final var flattenBuilder = source.render(renderer);
    final LoadableConstructor outputName =
        transforms.stream()
            .reduce(
                childName::render,
                (n, t) -> t.render(flattenBuilder, n),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
    return outputName;
  }

  @Override
  public EcmaLoadableConstructor render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    final var lambda = new FlattenLambda(name);
    builder.flatten(type, lambda);
    return lambda.name;
  }

  @Override
  public Optional<DestructuredArgumentNode> resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler) {
    final var innerDefs = defs.bind(name);
    if (!source.resolve(innerDefs, errorHandler)) {
      return Optional.empty();
    }
    final var nextName =
        transforms.stream()
            .reduce(
                Optional.of(childName),
                (n, t) -> n.flatMap(innerName -> t.resolve(innerName, innerDefs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    definedNames = name.targets().map(Target::name).collect(Collectors.toList());
    return nextName;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return source.resolveDefinitions(expressionCompilerServices, errorHandler)
        & transforms.stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size()
        & childName.resolve(expressionCompilerServices, errorHandler);
  }

  @Override
  public Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler)
        || !childName.typeCheck(source.streamType(), errorHandler)) {
      return Optional.empty();
    }
    ordering =
        transforms.stream()
            .reduce(
                source.ordering(),
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final var resultType =
        transforms.stream()
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
