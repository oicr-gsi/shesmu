package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AlgebraicImyhatNodeObject extends AlgebraicImyhatNode {

  private final String name;
  private final List<Pair<String, ImyhatNode>> types;

  public AlgebraicImyhatNodeObject(String name, List<Pair<String, ImyhatNode>> types) {
    super();
    this.name = name;
    this.types = types;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var fieldCounts =
        types.stream()
            .map(Pair::first)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (fieldCounts.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .peek(
                e ->
                    errorHandler.accept(
                        String.format(
                            "Object type has field %s %d times.", e.getKey(), e.getValue())))
            .count()
        > 0) {
      return Imyhat.BAD;
    }
    return Imyhat.algebraicObject(
        name,
        types.stream()
            .map(
                field ->
                    new Pair<>(
                        field.first(),
                        field.second().render(expressionCompilerServices, errorHandler))));
  }
}
