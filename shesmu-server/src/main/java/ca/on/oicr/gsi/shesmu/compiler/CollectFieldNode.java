package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CollectFieldNode {

  public static Parser parse(Parser parser, Consumer<CollectFieldNode> output) {
    final AtomicReference<String> fieldName = new AtomicReference<>();
    final AtomicReference<List<ListNode>> transforms = new AtomicReference<>();
    final AtomicReference<CollectNode> collector = new AtomicReference<>();
    final Parser result =
        parser
            .whitespace()
            .identifier(fieldName::set)
            .whitespace()
            .symbol("=")
            .whitespace()
            .list(transforms::set, ListNode::parse)
            .whitespace()
            .then(CollectNode::parse, collector::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(new CollectFieldNode(fieldName.get(), transforms.get(), collector.get()));
    }
    return result;
  }

  private final CollectNode collector;
  private final String fieldName;
  private final List<ListNode> transforms;

  public CollectFieldNode(String fieldName, List<ListNode> transforms, CollectNode collector) {
    this.fieldName = fieldName;
    this.transforms = transforms;
    this.collector = collector;
  }

  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    collector.collectFreeVariables(names, predicate);
    transforms.forEach(t -> t.collectFreeVariables(names, predicate));
  }

  public void collectPlugins(Set<Path> pluginFileNames) {
    transforms.forEach(transform -> transform.collectPlugins(pluginFileNames));
    collector.collectPlugins(pluginFileNames);
  }

  public Pair<String, Imyhat> field() {
    return new Pair<>(fieldName, collector.type());
  }

  public String fieldName() {
    return fieldName;
  }

  public boolean orderingCheck(Ordering initialOrder, Consumer<String> errorHandler) {
    final Ordering ordering =
        transforms
            .stream()
            .reduce(
                initialOrder,
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });

    return ordering != Ordering.BAD && collector.orderingCheck(ordering, errorHandler);
  }

  public void render(JavaStreamBuilder builder, LoadableConstructor initial) {
    collector.render(
        builder,
        transforms
            .stream()
            .reduce(
                initial,
                (n, transform) -> transform.render(builder, n),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                }));
    builder.renderer().methodGen().valueOf(collector.type().apply(TO_ASM));
    builder.renderer().methodGen().returnValue();
    builder.renderer().methodGen().endMethod();
  }

  public boolean resolve(
      NameDefinitions defs, DestructuredArgumentNode initial, Consumer<String> errorHandler) {
    final Optional<DestructuredArgumentNode> nextName =
        transforms
            .stream()
            .reduce(
                Optional.of(initial),
                (n, t) -> n.flatMap(name -> t.resolve(name, defs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });

    return nextName.map(name -> collector.resolve(name, defs, errorHandler)).orElse(false);
  }

  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return collector.resolveDefinitions(expressionCompilerServices, errorHandler)
        & transforms
                .stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size();
  }

  public Imyhat type() {
    return collector.type();
  }

  public boolean typeCheck(Imyhat sourceType, Consumer<String> errorHandler) {

    final Optional<Imyhat> resultType =
        transforms
            .stream()
            .reduce(
                Optional.of(sourceType),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    if (resultType.map(incoming -> collector.typeCheck(incoming, errorHandler)).orElse(false)) {
      return true;
    }
    return false;
  }
}
