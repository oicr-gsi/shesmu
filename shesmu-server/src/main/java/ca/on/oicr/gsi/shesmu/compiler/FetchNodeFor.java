package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class FetchNodeFor extends FetchNode {
  private final DestructuredArgumentNode name;
  private final SourceNode source;
  private final List<ListNode> transforms;
  private final FetchCollectNode collector;

  public FetchNodeFor(
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      FetchCollectNode collector) {
    super();
    this.name = name;
    this.source = source;
    this.transforms = transforms;
    this.collector = collector;
    name.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public Imyhat type() {
    return collector.type();
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final EcmaStreamBuilder builder = source.render(renderer);
    EcmaLoadableConstructor currentName = name::renderEcma;
    for (final ListNode transform : transforms) {
      currentName = transform.render(builder, currentName);
    }
    builder.map(currentName, Imyhat.BAD, collector::renderEcma);

    return String.format(
        "{type: \"%s\", compare: (a, b) => %s, operations: %s}",
        collector.operation(),
        collector.comparatorType().apply(EcmaScriptRenderer.COMPARATOR),
        builder.finish());
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = source.resolve(defs, errorHandler);

    final Optional<DestructuredArgumentNode> nextName =
        transforms.stream()
            .reduce(
                Optional.of(name),
                (n, t) -> n.flatMap(name -> t.resolve(name, defs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });

    ok =
        ok
            && nextName
                .map(
                    name -> {
                      final NameDefinitions collectorName =
                          defs.replaceStream(name.targets(), true);
                      return collector.resolve(collectorName, errorHandler);
                    })
                .orElse(false);
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return name.resolve(expressionCompilerServices, errorHandler)
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD
        & source.resolveDefinitions(expressionCompilerServices, errorHandler)
        & transforms.stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size()
        & collector.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler) || !name.typeCheck(source.streamType(), errorHandler)) {
      return false;
    }

    final var resultType =
        transforms.stream()
            .reduce(
                Optional.of(source.streamType()),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final var ordering =
        transforms.stream()
            .reduce(
                source.ordering(),
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    return resultType.isPresent()
        && collector.typeCheck(resultType.get(), errorHandler)
        && ordering != Ordering.BAD;
  }
}
