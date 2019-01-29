package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/** Append a Shesmu value onto a JSON array */
public final class PackJsonArray implements ImyhatConsumer {
  private final ArrayNode node;

  public PackJsonArray(ArrayNode node) {
    this.node = node;
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    final ArrayNode array = node.addArray();
    fields
        .sorted(Comparator.comparing(Field::index))
        .forEach(f -> f.type().accept(new PackJsonArray(array), f.value()));
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    final ObjectNode object = node.addObject();
    fields.forEach(f -> f.type().accept(new PackJsonObject(object, f.index()), f.value()));
  }

  @Override
  public void accept(String value) {
    node.add(value);
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    final ArrayNode array = node.addArray();
    values.forEach(v -> inner.accept(new PackJsonArray(array), v));
  }

  @Override
  public void accept(Path value) {
    node.add(value.toString());
  }

  @Override
  public void accept(long value) {
    node.add(value);
  }

  @Override
  public void accept(Instant value) {
    node.add(value.toEpochMilli());
  }

  @Override
  public void accept(boolean value) {
    node.add(value);
  }
}
