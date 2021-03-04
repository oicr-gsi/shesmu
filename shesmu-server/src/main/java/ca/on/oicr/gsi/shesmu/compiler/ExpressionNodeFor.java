package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeFor extends ExpressionNode {

  private final CollectNode collector;
  private final DestructuredArgumentNode name;

  private final SourceNode source;
  private final List<ListNode> transforms;

  public ExpressionNodeFor(
      int line,
      int column,
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      CollectNode collector) {
    super(line, column);
    this.name = name;
    this.source = source;
    this.transforms = transforms;
    this.collector = collector;
    name.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    source.collectFreeVariables(names, predicate);
    collector.collectFreeVariables(names, predicate);
    transforms.forEach(t -> t.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    source.collectPlugins(pluginFileNames);
    transforms.forEach(transform -> transform.collectPlugins(pluginFileNames));
    collector.collectPlugins(pluginFileNames);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final EcmaStreamBuilder builder = source.render(renderer);
    return collector.render(
        builder,
        transforms.stream()
            .reduce(
                loader -> name.renderEcma(loader),
                (n, transform) -> transform.render(builder, n),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                }));
  }

  @Override
  public void render(Renderer renderer) {
    final JavaStreamBuilder builder = source.render(renderer);
    collector.render(
        builder,
        transforms.stream()
            .reduce(
                name::render,
                (n, transform) -> transform.render(builder, n),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                }));
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

    ok = ok && nextName.map(name -> collector.resolve(name, defs, errorHandler)).orElse(false);
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return source.resolveDefinitions(expressionCompilerServices, errorHandler)
        & collector.resolveDefinitions(expressionCompilerServices, errorHandler)
        & transforms.stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size()
        & name.resolve(expressionCompilerServices, errorHandler)
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD;
  }

  @Override
  public Imyhat type() {
    return collector.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler) || !name.typeCheck(source.streamType(), errorHandler)) {
      return false;
    }
    final Ordering ordering =
        transforms.stream()
            .reduce(
                source.ordering(),
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final Optional<Imyhat> resultType =
        transforms.stream()
            .reduce(
                Optional.of(source.streamType()),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    if (resultType.map(incoming -> collector.typeCheck(incoming, errorHandler)).orElse(false)
        && ordering != Ordering.BAD) {
      return collector.orderingCheck(ordering, errorHandler);
    }
    return false;
  }
}
