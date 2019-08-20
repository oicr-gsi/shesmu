package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackWdlVariables implements ImyhatConsumer {
  public static Pair<Function<ObjectNode, ImyhatConsumer>, Imyhat> create(
      Stream<Pair<String[], Imyhat>> input) {
    return create(input, 0);
  }
  /**
   * Take a part a ragged x.y.z = type structure from a WDL file and turn it into nested Shesmu
   * objects with the knowledge to reconstruct the WDL structure.
   */
  public static Pair<Function<ObjectNode, ImyhatConsumer>, Imyhat> create(
      Stream<Pair<String[], Imyhat>> input, int index) {
    final Map<String, List<Pair<String[], Imyhat>>> groups =
        input.collect(Collectors.groupingBy(p -> p.first()[index], Collectors.toList()));
    final List<Pair<String, Imyhat>> fields = new ArrayList<>();
    final Map<String, Function<ObjectNode, ImyhatConsumer>> handlers = new HashMap<>();

    for (final Map.Entry<String, List<Pair<String[], Imyhat>>> group : groups.entrySet()) {
      // If we've hit the end of this nested structure
      if (index + 1 == group.getValue().stream().mapToInt(x -> x.first().length).max().getAsInt()) {
        for (final Pair<String[], Imyhat> field : group.getValue()) {
          final String fieldName = field.first()[index];
          final String propertyName = String.join(".", field.first());
          handlers.put(fieldName, result -> new PackJsonObject(result, propertyName));
          fields.add(new Pair<>(fieldName, field.second()));
        }
      } else {
        final Pair<Function<ObjectNode, ImyhatConsumer>, Imyhat> inner =
            create(group.getValue().stream(), index + 1);
        handlers.put(group.getKey(), inner.first());
        fields.add(new Pair<>(group.getKey(), inner.second()));
      }
    }

    return new Pair<>(
        result -> new PackWdlVariables(result, handlers), new Imyhat.ObjectImyhat(fields.stream()));
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
  public void accept(Imyhat inner, Optional<?> value) {
    value.ifPresent(o -> inner.accept(this, o));
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
