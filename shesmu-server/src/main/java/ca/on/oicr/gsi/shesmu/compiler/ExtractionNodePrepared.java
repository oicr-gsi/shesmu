package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ExtractionNodePrepared extends ExtractionNode {
  private List<ExtractionNode> inner;
  private final String name;

  protected ExtractionNodePrepared(int line, int column, String name) {
    super(line, column);
    this.name = name;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    for (final var node : inner) {
      node.collectFreeVariables(names, predicate);
    }
  }

  @Override
  public Stream<String> names() {
    return inner.stream().flatMap(ExtractionNode::names);
  }

  @Override
  public void render(OutputCollector outputCollector) {
    for (final var node : inner) {
      node.render(outputCollector);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return inner.stream().filter(i -> i.resolve(defs, errorHandler)).count() == inner.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.stream()
            .filter(i -> i.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == inner.size();
  }

  @Override
  public boolean resolvePrepared(
      String context,
      Map<String, List<ExtractionNode>> prepared,
      Set<String> used,
      Consumer<String> errorHandler) {
    if (used.contains(name)) {
      return true;
    }
    if (prepared.containsKey(name)) {
      inner = prepared.get(name);
      used.add(name);
      final var innerContext = String.format("%s %s:%d:%d", context, name, line(), column());
      return inner.stream()
              .filter(i -> i.resolvePrepared(innerContext, prepared, used, errorHandler))
              .count()
          == inner.size();
    } else {
      errorHandler.accept(
          String.format(
              "%s%d:%d: Prepared columns “%s” are not found.", context, line(), column(), name));
      return false;
    }
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler, OutputFormat outputFormat) {
    return inner.stream().filter(i -> i.typeCheck(errorHandler, outputFormat)).count()
        == inner.size();
  }
}
