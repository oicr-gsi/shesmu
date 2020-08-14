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
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Append a Shesmu value onto a JSON array */
public class PackJsonArray implements ImyhatConsumer {
  protected final ArrayNode node;

  public PackJsonArray(ArrayNode node) {
    this.node = node;
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      inner.accept(this, value.get());
    } else {
      node.addNull();
    }
  }

  @Override
  public void accept(String typeName, Consumer<ImyhatConsumer> accessor) {
    final ObjectNode algebraic = node.addObject();
    algebraic.put("type", typeName);
    accessor.accept(createObject(algebraic, "contents"));
  }

  @Override
  public void accept(double value) {
    node.add(value);
  }

  @Override
  public void accept(String value) {
    node.add(value);
  }

  @Override
  public void accept(JsonNode value) {
    node.add(value);
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    final ArrayNode array = node.addArray();
    values.forEach(v -> inner.accept(createArray(array), v));
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

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    if (key.isSame(Imyhat.STRING)) {
      final ObjectNode inner = node.addObject();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        value.accept(new PackJsonObject(inner, (String) entry.getKey()), entry.getValue());
      }
    } else {
      final ArrayNode inner = node.addArray();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        final PackJsonArray row = new PackJsonArray(inner.addArray());
        key.accept(row, entry.getKey());
        value.accept(row, entry.getValue());
      }
    }
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    final ObjectNode object = node.addObject();
    fields.forEach(f -> f.type().accept(createObject(object, f.index()), f.value()));
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    final ArrayNode array = node.addArray();
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
