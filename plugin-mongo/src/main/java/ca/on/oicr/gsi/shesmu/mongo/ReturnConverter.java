package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.bson.Document;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = ReturnConverter.Deserializer.class)
public abstract class ReturnConverter {
  public static class Deserializer extends ValueDeserializer<ReturnConverter> {

    private ReturnConverter deserialize(JsonNode node) {
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
          case "keyvalue":
            return KEY_VALUE;
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
            return list(deserialize(node));
          case "optional":
            return deserialize(node.get("of")).asOptional();
          case "object":
            return object(elements(node));
          case "unwrap":
            return unwrap(node.get("name").asString(), deserialize(node.get("of")));
          default:
            throw new IllegalArgumentException("Unknown Mongo type: " + type);
        }
      }
      throw new IllegalArgumentException("Cannot parse Mongo type: " + node.getNodeType());
    }

    @Override
    public ReturnConverter deserialize(JsonParser parser, DeserializationContext context) {
      final JsonNode node = context.readTree(parser);
      return deserialize(node);
    }

    private Map<String, ReturnConverter> elements(JsonNode node) {
      final Map<String, ReturnConverter> elements = new TreeMap<>();
      final var iterator = node.get("of").properties().iterator();
      while (iterator.hasNext()) {
        final var current = iterator.next();
        elements.put(current.getKey(), deserialize(current.getValue()));
      }
      return elements;
    }
  }

  public static ReturnConverter list(ReturnConverter inner) {
    return new ReturnConverter() {
      private final Imyhat type = inner.type().asList();

      @Override
      Imyhat type() {
        return type;
      }

      @SuppressWarnings("unchecked")
      @Override
      Object unpack(Document document, String name) {
        return ((List<Document>) document.get(name))
            .stream().map(inner::unpackRoot).collect(Collectors.toSet());
      }
    };
  }

  public static ReturnConverter object(Map<String, ReturnConverter> elements) {
    return new ReturnConverter() {
      private final Imyhat type = typeFrom(elements);

      @Override
      Imyhat type() {
        return type;
      }

      @Override
      Object unpack(Document document, String name) {
        return unpackRoot(document.get(name, Document.class));
      }

      @Override
      Object unpackRoot(Document document) {
        return new Tuple(
            elements.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(element -> element.getValue().unpack(document, element.getKey()))
                .toArray());
      }
    };
  }

  public static Imyhat typeFrom(Map<String, ReturnConverter> elements) {
    return new Imyhat.ObjectImyhat(
        elements.entrySet().stream()
            .map(element -> new Pair<>(element.getKey(), element.getValue().type())));
  }

  public static ReturnConverter unwrap(String innerName, ReturnConverter inner) {
    return new ReturnConverter() {
      @Override
      Imyhat type() {
        return inner.type();
      }

      @Override
      Object unpack(Document document, String name) {
        return inner.unpack(document.get(name, Document.class), innerName);
      }
    };
  }

  private static final Imyhat ATTR = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING);
  public static final ReturnConverter BOOLEAN =
      new ReturnConverter() {

        @Override
        Imyhat type() {
          return Imyhat.BOOLEAN;
        }

        @Override
        Object unpack(Document document, String name) {
          return document.getBoolean(name);
        }
      };
  public static final ReturnConverter DATE =
      new ReturnConverter() {

        @Override
        Imyhat type() {
          return Imyhat.DATE;
        }

        @Override
        Object unpack(Document document, String name) {
          return document.getDate(name).toInstant();
        }
      };
  public static final ReturnConverter FLOAT =
      new ReturnConverter() {

        @Override
        Imyhat type() {
          return Imyhat.FLOAT;
        }

        @Override
        Object unpack(Document document, String name) {
          return document.getDouble(name);
        }
      };
  public static final ReturnConverter INTEGER =
      new ReturnConverter() {

        @Override
        Imyhat type() {
          return Imyhat.INTEGER;
        }

        @Override
        Object unpack(Document document, String name) {
          return document.getLong(name);
        }
      };
  public static final ReturnConverter KEY_VALUE =
      new ReturnConverter() {
        @Override
        Imyhat type() {
          return ATTR.asList();
        }

        @Override
        Object unpack(Document document, String name) {
          return unpackRoot((Document) document.get(name));
        }

        @Override
        Object unpackRoot(Document document) {
          return document.entrySet().stream()
              .map(e -> new Tuple(e.getKey(), Objects.toString(e.getValue())))
              .collect(Collectors.toCollection(ATTR::newSet));
        }
      };
  public static final ReturnConverter PATH =
      new ReturnConverter() {
        @Override
        Imyhat type() {
          return Imyhat.PATH;
        }

        @Override
        Object unpack(Document document, String name) {
          final var path = document.getString(name);
          return path == null ? null : Paths.get(path);
        }
      };
  public static final ReturnConverter STRING =
      new ReturnConverter() {

        @Override
        Imyhat type() {
          return Imyhat.STRING;
        }

        @Override
        Object unpack(Document document, String name) {
          return document.getString(name);
        }
      };

  public ReturnConverter asOptional() {
    final var inner = this;
    return new ReturnConverter() {
      public ReturnConverter asOptional() {
        return this;
      }

      @Override
      Imyhat type() {
        return inner.type();
      }

      @Override
      Object unpack(Document document, String name) {
        return Optional.ofNullable(inner.unpack(document, name));
      }
    };
  }

  abstract Imyhat type();

  abstract Object unpack(Document document, String name);

  Object unpackRoot(Document document) {
    throw new UnsupportedOperationException();
  }
}
