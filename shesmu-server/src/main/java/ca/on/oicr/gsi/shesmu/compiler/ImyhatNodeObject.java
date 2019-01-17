package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ImyhatNodeObject extends ImyhatNode {

  private final List<Pair<String, ImyhatNode>> types;

  public ImyhatNodeObject(List<Pair<String, ImyhatNode>> types) {
    this.types = types;
  }

  @Override
  public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    final Map<String, Long> fieldCounts =
        types
            .stream()
            .map(Pair::first)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    if (fieldCounts
            .entrySet()
            .stream()
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
    return new Imyhat.ObjectImyhat(
        types
            .stream()
            .map(
                field ->
                    new Pair<>(field.first(), field.second().render(definedTypes, errorHandler))));
  }
}
