package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Add a Shesmu value into a JSON object */
public class PackJsonObject implements ImyhatConsumer {
  protected final String name;
  protected final ObjectNode node;

  public PackJsonObject(ObjectNode node, String name) {
    this.node = node;
    this.name = name;
  }

  @Override
  public void accept(String value) {
    node.put(name, value);
  }

  @Override
  public void accept(JsonNode value) {
    node.set(name, value);
  }

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    if (key.isSame(Imyhat.STRING)) {
      final ObjectNode inner = node.putObject(name);
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        value.accept(new PackJsonObject(inner, (String) entry.getKey()), entry.getValue());
      }
    } else {
      final ArrayNode inner = node.putArray(name);
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        final PackJsonArray row = new PackJsonArray(inner.addArray());
        key.accept(row, entry.getKey());
        value.accept(row, entry.getValue());
      }
    }
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    final ArrayNode array = node.putArray(name);
    values.forEach(v -> inner.accept(createArray(array), v));
  }

  @Override
  public void accept(long value) {
    node.put(name, value);
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      inner.accept(this, value.get());
    } else {
      node.putNull(name);
    }
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

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    final ObjectNode object = node.putObject(name);
    fields.forEach(f -> f.type().accept(createObject(object, f.index()), f.value()));
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    final ArrayNode array = node.putArray(name);
    fields
        .sorted(Comparator.comparing(Field::index))
        .forEach(f -> f.type().accept(createArray(array), f.value()));
  }

  protected ImyhatConsumer createArray(ArrayNode array) {
    return new PackJsonArray(array);
  }

  protected ImyhatConsumer createObject(ObjectNode object, String property) {
    return new PackJsonObject(object, property);
  }
}
