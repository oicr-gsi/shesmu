package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.bson.*;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ParameterConverter.Deserializer.class)
public abstract class ParameterConverter {

  public static class Deserializer extends ValueDeserializer<ParameterConverter> {

    private ParameterConverter deserialize(JsonNode node) {
      if (node.isString()) {
        final var str = node.asString();
        switch (str) {
          case "boolean":
            return BOOLEAN;
          case "date":
            return DATE;
          case "float":
          case "double":
            return FLOAT;
          case "integer":
            return INTEGER;
          case "path":
            return PATH;
          case "string":
            return STRING;
          default:
            throw new IllegalArgumentException("Unknown Monogo type: " + str);
        }
      }
      if (node.isObject()) {
        final var type = node.get("is").asString();
        switch (type) {
          case "list":
            return list(deserialize(node.get("of")));
          case "optional":
            return deserialize(node.get("of")).asOptional();
          case "object":
            final Map<String, ParameterConverter> elements = new TreeMap<>();
            final var iterator = node.get("of").properties().iterator();
            while (iterator.hasNext()) {
              final var current = iterator.next();
              elements.put(current.getKey(), deserialize(current.getValue()));
            }
            return object(elements);
          default:
            throw new IllegalArgumentException("Unknown Mongo type: " + type);
        }
      }
      throw new IllegalArgumentException("Cannot parse Mongo type: " + node.getNodeType());
    }

    @Override
    public ParameterConverter deserialize(JsonParser parser, DeserializationContext context) {
      final JsonNode node = context.readTree(parser);
      return deserialize(node);
    }
  }

  public static ParameterConverter list(ParameterConverter inner) {
    return new ParameterConverter() {

      @Override
      BsonValue pack(Object value) {
        return new BsonArray(
            ((Set<?>) value).stream().map(inner::pack).collect(Collectors.toList()));
      }

      @Override
      Imyhat type() {
        return inner.type().asList();
      }
    };
  }

  public static ParameterConverter object(Map<String, ParameterConverter> elements) {
    return new ParameterConverter() {
      private final Imyhat type =
          new Imyhat.ObjectImyhat(
              elements.entrySet().stream()
                  .map(element -> new Pair<>(element.getKey(), element.getValue().type())));

      @Override
      BsonValue pack(Object value) {
        final var tuple = (Tuple) value;
        final var result = new BsonDocument();
        elements.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(
                new Consumer<>() {
                  private int index;

                  @Override
                  public void accept(Map.Entry<String, ParameterConverter> entry) {
                    result.put(entry.getKey(), entry.getValue().pack(tuple.get(index++)));
                  }
                });
        return result;
      }

      @Override
      Imyhat type() {
        return type;
      }
    };
  }

  public static final ParameterConverter BOOLEAN =
      new ParameterConverter() {

        @Override
        BsonValue pack(Object value) {
          return new BsonBoolean((boolean) value);
        }

        @Override
        Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  public static final ParameterConverter DATE =
      new ParameterConverter() {

        @Override
        BsonValue pack(Object value) {
          return new BsonDateTime(((Instant) value).toEpochMilli());
        }

        @Override
        Imyhat type() {
          return Imyhat.DATE;
        }
      };
  public static final ParameterConverter FLOAT =
      new ParameterConverter() {

        @Override
        BsonValue pack(Object value) {
          return new BsonDouble((double) value);
        }

        @Override
        Imyhat type() {
          return Imyhat.FLOAT;
        }
      };
  public static final ParameterConverter INTEGER =
      new ParameterConverter() {

        @Override
        BsonValue pack(Object value) {
          return new BsonInt64((long) value);
        }

        @Override
        Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  public static final ParameterConverter PATH =
      new ParameterConverter() {
        @Override
        BsonValue pack(Object value) {
          return new BsonString(value.toString());
        }

        @Override
        Imyhat type() {
          return Imyhat.PATH;
        }
      };
  public static final ParameterConverter STRING =
      new ParameterConverter() {

        @Override
        BsonValue pack(Object value) {
          return new BsonString((String) value);
        }

        @Override
        Imyhat type() {
          return Imyhat.STRING;
        }
      };

  public ParameterConverter asOptional() {
    final var inner = this;
    return new ParameterConverter() {
      public ParameterConverter asOptional() {
        return this;
      }

      @Override
      BsonValue pack(Object value) {

        return ((Optional<?>) value).map(inner::pack).orElseGet(BsonNull::new);
      }

      @Override
      Imyhat type() {
        return inner.type().asOptional();
      }
    };
  }

  abstract BsonValue pack(Object value);

  abstract Imyhat type();
}
