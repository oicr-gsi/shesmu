package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class AsJsonNode implements ImyhatFunction<JsonNode> {
  public static JsonNode convert(Imyhat type, Object value) {
    return type.apply(new AsJsonNode(), value);
  }

  private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

  @Override
  public JsonNode apply(String name, AccessContents accessor) {
    final ObjectNode objectNode = FACTORY.objectNode();
    objectNode.put("type", name);
    objectNode.set("value", accessor.apply(this));
    return objectNode;
  }

  @Override
  public JsonNode apply(boolean value) {
    return FACTORY.booleanNode(value);
  }

  @Override
  public JsonNode apply(double value) {
    return FACTORY.numberNode(value);
  }

  @Override
  public JsonNode apply(Instant value) {
    return FACTORY.numberNode(value.toEpochMilli());
  }

  @Override
  public JsonNode apply(long value) {
    return FACTORY.numberNode(value);
  }

  @Override
  public JsonNode apply(Stream<Object> values, Imyhat inner) {
    final ArrayNode arrayNode = FACTORY.arrayNode();
    values.forEach(v -> arrayNode.add(inner.apply(this, v)));
    return arrayNode;
  }

  @Override
  public JsonNode apply(String value) {
    return FACTORY.textNode(value);
  }

  @Override
  public JsonNode apply(Path value) {
    return FACTORY.textNode(value.toString());
  }

  @Override
  public JsonNode apply(Imyhat inner, Optional<?> value) {
    return value.map(v -> inner.apply(this, v)).orElse(FACTORY.nullNode());
  }

  @Override
  public JsonNode apply(JsonNode value) {
    return value;
  }

  @Override
  public JsonNode applyMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    if (key.isSame(Imyhat.STRING)) {
      final ObjectNode objectNode = FACTORY.objectNode();
      map.forEach((k, v) -> objectNode.put((String) k, value.apply(this, v)));
      return objectNode;
    }
    final ArrayNode mapNode = FACTORY.arrayNode();
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      final ArrayNode pairNode = mapNode.addArray();
      pairNode.add(key.apply(this, entry.getKey()));
      pairNode.add(value.apply(this, entry.getValue()));
    }
    return mapNode;
  }

  @Override
  public JsonNode applyObject(Stream<Field<String>> contents) {
    final ObjectNode objectNode = FACTORY.objectNode();
    contents.forEach(
        field -> objectNode.set(field.index(), field.type().apply(this, field.value())));
    return objectNode;
  }

  @Override
  public JsonNode applyTuple(Stream<Field<Integer>> contents) {
    final ArrayNode tupleNode = FACTORY.arrayNode();
    contents
        .sorted(Comparator.comparing(Field::index))
        .forEach(field -> tupleNode.add(field.type().apply(this, field.value())));
    return tupleNode;
  }
}
