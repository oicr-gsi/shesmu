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

/** Add a Shesmu value into a JSON object */
public final class PackJsonObject implements ImyhatConsumer {
  private final ObjectNode node;
  private final String name;

  public PackJsonObject(ObjectNode node, String name) {
    this.node = node;
    this.name = name;
  }

  @Override
  public void accept(String value) {
    node.put(name, value);
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    final ArrayNode array = node.putArray(name);
    values.forEach(v -> inner.accept(new PackJsonArray(array), v));
  }

  @Override
  public void accept(long value) {
    node.put(name, value);
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    final ArrayNode array = node.putArray(name);
    fields
        .sorted(Comparator.comparing(Field::index))
        .forEach(f -> f.type().accept(new PackJsonArray(array), f.value()));
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    final ObjectNode object = node.putObject(name);
    fields.forEach(f -> f.type().accept(new PackJsonObject(object, f.index()), f.value()));
  }

  @Override
  public void accept(Instant value) {
    node.put(name, value.toEpochMilli());
  }

  @Override
  public void accept(Path value) {
    node.put(name, value.toString());
  }

  @Override
  public void accept(boolean value) {
    node.put(name, value);
  }

  @Override
  public void accept(double value) {
    node.put(name, value);
  }
}
