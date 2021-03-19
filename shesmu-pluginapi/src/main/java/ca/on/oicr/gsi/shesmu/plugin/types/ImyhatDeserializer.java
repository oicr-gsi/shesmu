package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

public class ImyhatDeserializer extends JsonDeserializer<Imyhat> {

  private Imyhat deserialize(JsonNode node) {
    if (node.isTextual()) {
      return Imyhat.parse(node.asText());
    }
    if (node.isArray()) {
      final var elements = new Imyhat[node.size()];
      for (var i = 0; i < elements.length; i++) {
        elements[i] = deserialize(node.get(i));
      }
      return Imyhat.tuple(elements);
    }
    if (node.isObject()) {
      final var type = node.get("is").asText();
      switch (type) {
        case "optional":
          return deserialize(node.get("inner")).asOptional();
        case "list":
          return deserialize(node.get("inner")).asList();
        case "dictionary":
          return Imyhat.dictionary(deserialize(node.get("key")), deserialize(node.get("value")));
        case "object":
          return new ObjectImyhat(
              Utils.stream(node.get("fields").fields())
                  .map(e -> new Pair<>(e.getKey(), deserialize(e.getValue()))));
        case "algebraic":
          return Utils.stream((node.get("union")).fields())
              .map(e -> deserializeAlgebraic(e.getKey(), e.getValue()))
              .reduce(Imyhat::unify)
              .orElse(Imyhat.BAD);
        default:
          throw new IllegalArgumentException("Unknown type: " + type);
      }
    }
    throw new IllegalArgumentException("Cannot parse type: " + node.getNodeType());
  }

  @Override
  public Imyhat deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    final var oc = parser.getCodec();
    final JsonNode node = oc.readTree(parser);
    return deserialize(node);
  }

  private Imyhat deserializeAlgebraic(String name, JsonNode node) {
    if (node.isNull()) {
      return Imyhat.algebraicTuple(name);
    }
    if (node.isArray()) {
      final var elements = new Imyhat[node.size()];
      for (var i = 0; i < elements.length; i++) {
        elements[i] = deserialize(node.get(i));
      }
      return Imyhat.algebraicTuple(name, elements);
    }
    if (node.isObject()) {
      if (node.size() > 0) {
        return Imyhat.algebraicObject(
            name,
            Utils.stream(node.fields())
                .map(e -> new Pair<>(e.getKey(), deserialize(e.getValue()))));
      } else {
        return Imyhat.algebraicTuple(name);
      }
    }
    return Imyhat.BAD;
  }
}
