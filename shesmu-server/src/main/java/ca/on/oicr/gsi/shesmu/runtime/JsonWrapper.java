package ca.on.oicr.gsi.shesmu.runtime;

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
import java.util.Optional;
import java.util.stream.Stream;

public class JsonWrapper implements ImyhatFunction<JsonNode> {
  public static JsonNode convert(Imyhat type, Object value) {
    return type.apply(new JsonWrapper(), value);
  }

  private static final JsonNodeFactory FACTORY = JsonNodeFactory.instance;

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
  public JsonNode applyObject(Stream<Field<String>> contents) {
    final ObjectNode ObjectNode = FACTORY.objectNode();
    contents.forEach(
        field -> ObjectNode.put(field.index(), field.type().apply(this, field.value())));
    return ObjectNode;
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
