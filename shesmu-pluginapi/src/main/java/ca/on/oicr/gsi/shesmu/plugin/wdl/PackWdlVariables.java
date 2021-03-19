package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackWdlVariables implements ImyhatConsumer {
  public static Pair<Function<ObjectNode, ImyhatConsumer>, Imyhat> create(
      Stream<Pair<String[], Imyhat>> input) {
    return create(
        (propertyName, type) -> result -> new PackWdlJsonObject(result, propertyName, true),
        handlers -> result -> new PackWdlVariables(result, handlers),
        Function.identity(),
        input,
        0);
  }
  /**
   * Take a part a ragged x.y.z = type structure from a WDL file and turn it into nested Shesmu
   * objects with the knowledge to reconstruct the WDL structure.
   */
  public static <T, I> Pair<T, Imyhat> create(
      BiFunction<String, I, T> propertyCreator,
      Function<Map<String, T>, T> aggregator,
      Function<I, Imyhat> typeGetter,
      Stream<Pair<String[], I>> input,
      int index) {
    final var groups =
        input.collect(Collectors.groupingBy(p -> p.first()[index], Collectors.toList()));
    final List<Pair<String, Imyhat>> fields = new ArrayList<>();
    final Map<String, T> handlers = new HashMap<>();

    for (final var group : groups.entrySet()) {
      // If we've hit the end of this nested structure
      if (index + 1 == group.getValue().stream().mapToInt(x -> x.first().length).max().getAsInt()) {
        for (final var field : group.getValue()) {
          final var fieldName = field.first()[index];
          final var propertyName = String.join(".", field.first());
          handlers.put(fieldName, propertyCreator.apply(propertyName, field.second()));
          fields.add(new Pair<>(fieldName, typeGetter.apply(field.second())));
        }
      } else {
        final var inner =
            create(propertyCreator, aggregator, typeGetter, group.getValue().stream(), index + 1);
        handlers.put(group.getKey(), inner.first());
        fields.add(new Pair<>(group.getKey(), inner.second()));
      }
    }

    return new Pair<>(aggregator.apply(handlers), new Imyhat.ObjectImyhat(fields.stream()));
  }

  private final Map<String, Function<ObjectNode, ImyhatConsumer>> handlers;
  private final ObjectNode result;

  public PackWdlVariables(
      ObjectNode result, Map<String, Function<ObjectNode, ImyhatConsumer>> handlers) {
    this.result = result;
    this.handlers = handlers;
  }

  @Override
  public void accept(boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Instant value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Path value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(JsonNode value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    value.ifPresent(o -> inner.accept(this, o));
  }

  @Override
  public void accept(String name, Consumer<ImyhatConsumer> accessor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    fields.forEach(f -> f.type().accept(handlers.get(f.index()).apply(result), f.value()));
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    throw new UnsupportedOperationException();
  }
}
