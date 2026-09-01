package ca.on.oicr.gsi.shesmu.mongo;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bson.*;
import org.bson.conversions.Bson;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = QueryBuilder.Deserializer.class)
public interface QueryBuilder {
  class Deserializer extends ValueDeserializer<QueryBuilder> {

    @Override
    public QueryBuilder deserialize(
        JsonParser parser, DeserializationContext deserializationContext) {
      final JsonNode node = deserializationContext.readTree(parser);
      return deserialize(node);
    }

    private QueryBuilder deserialize(JsonNode node) {
      switch (node.getNodeType()) {
        case ARRAY:
          final var builders = new QueryBuilder[node.size()];
          var index = 0;
          for (final var inner : node) {
            builders[index++] = deserialize(inner);
          }
          return list(builders);
        case BOOLEAN:
          return literal(new BsonBoolean(node.asBoolean()));
        case NULL:
          return literal(null);
        case NUMBER:
          return literal(
              node.isDouble() ? new BsonDouble(node.asDouble()) : new BsonInt32(node.asInt()));
        case OBJECT:
          if (node.has("$$parameter")) {
            return parameter(node.get("$$parameter").asInt());
          }
          final Map<String, QueryBuilder> fields = new TreeMap<>();
          final var iterator = node.properties().iterator();
          while (iterator.hasNext()) {
            final var entry = iterator.next();
            fields.put(entry.getKey(), deserialize(entry.getValue()));
          }
          return object(fields);
        case STRING:
          return literal(new BsonString(node.asString()));
        default:
          throw new UnsupportedOperationException();
      }
    }
  }

  static QueryBuilder list(QueryBuilder... builders) {
    return new QueryBuilder() {
      @Override
      public BsonValue build(BsonValue... args) {
        return new BsonArray(
            Stream.of(builders).map(builder -> builder.build(args)).collect(Collectors.toList()));
      }

      @Override
      public Bson buildRoot(BsonValue... args) {
        throw new UnsupportedOperationException();
      }
    };
  }

  static QueryBuilder literal(BsonValue value) {
    return new QueryBuilder() {
      @Override
      public BsonValue build(BsonValue... args) {
        return value;
      }

      @Override
      public Bson buildRoot(BsonValue... args) {
        throw new UnsupportedOperationException();
      }
    };
  }

  static QueryBuilder object(Map<String, QueryBuilder> builders) {
    return new QueryBuilder() {
      @Override
      public BsonValue build(BsonValue... args) {

        return prepare(args);
      }

      @Override
      public Bson buildRoot(BsonValue... args) {
        return prepare(args);
      }

      BsonDocument prepare(BsonValue... args) {
        final var document = new BsonDocument();
        for (final var builder : builders.entrySet()) {
          document.put(builder.getKey(), builder.getValue().build(args));
        }
        return document;
      }
    };
  }

  static QueryBuilder parameter(int position) {
    return new QueryBuilder() {
      @Override
      public BsonValue build(BsonValue... args) {
        return args[position];
      }

      @Override
      public Bson buildRoot(BsonValue... args) {
        throw new UnsupportedOperationException();
      }
    };
  }

  BsonValue build(BsonValue... args);

  Bson buildRoot(BsonValue... args);
}
