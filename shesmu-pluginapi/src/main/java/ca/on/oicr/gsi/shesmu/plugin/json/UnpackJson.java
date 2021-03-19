package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Parse a JSON value based on a type */
public class UnpackJson implements ImyhatTransformer<Object> {

  private final JsonNode value;
  private final TimeFormat format;

  public UnpackJson(JsonNode value) {
    this(value, TimeFormat.MILLIS_NUMERIC);
  }

  public UnpackJson(JsonNode value, TimeFormat format) {
    this.value = value;
    this.format = format;
  }

  @Override
  public Object algebraic(Stream<AlgebraicTransformer> contents) {
    final var type = value.get("type").asText();
    return contents
        .filter(a -> a.name().equals(type))
        .findFirst()
        .get()
        .visit(
            new AlgebraicVisitor<>() {
              @Override
              public Object empty(String name) {
                return new AlgebraicValue(name);
              }

              @Override
              public Object object(String name, Stream<Pair<String, Imyhat>> contents) {
                final var object = value.get("contents");
                return new AlgebraicValue(
                    name,
                    contents
                        .map(p -> p.second().apply(new UnpackJson(object.get(p.first()), format)))
                        .toArray());
              }

              @Override
              public Object tuple(String name, Stream<Imyhat> contents) {
                final var tuple = value.get("contents");
                return new AlgebraicValue(
                    name,
                    contents
                        .map(Pair.number())
                        .map(p -> p.second().apply(new UnpackJson(tuple.get(p.first()), format)))
                        .toArray());
              }
            });
  }

  @Override
  public Object bool() {
    return value.asBoolean();
  }

  @Override
  public Object date() {
    if (value == null || value.isNull()) {
      return Instant.EPOCH;
    } else if (value.isNumber() && format == TimeFormat.MILLIS_NUMERIC) {
      return Instant.ofEpochMilli(value.asLong());
    } else if (value.isNumber() && format == TimeFormat.SECONDS_NUMERIC) {
      return Instant.ofEpochMilli((long) (1000 * value.asDouble()));
    } else {
      return DateTimeFormatter.ISO_INSTANT.parse(value.asText(), Instant::from);
    }
  }

  @Override
  public Object floating() {
    return value.asDouble();
  }

  @Override
  public Object integer() {
    return value.asLong();
  }

  @Override
  public Object json() {
    return value;
  }

  @Override
  public Object list(Imyhat inner) {
    return IntStream.range(0, value.size())
        .mapToObj(i -> inner.apply(new UnpackJson(value.get(i))))
        .collect(inner.toSet());
  }

  @Override
  public Object map(Imyhat key, Imyhat value) {
    @SuppressWarnings("unchecked")
    final var comparator = (Comparator<Object>) key.comparator();
    final SortedMap<Object, Object> map = new TreeMap<>(comparator);
    if (key.isSame(Imyhat.STRING) && this.value.isObject()) {
      final var fields = this.value.fields();
      while (fields.hasNext()) {
        final var field = fields.next();
        map.put(field.getKey(), value.apply(new UnpackJson(field.getValue())));
      }
      return map;
    }
    if (!this.value.isArray()) {
      throw new IllegalArgumentException("Invalid JSON for map");
    }
    for (final var element : this.value) {
      if (!element.isArray() || element.size() != 2) {
        throw new IllegalArgumentException("Invalid JSON for map");
      }
      map.put(
          key.apply(new UnpackJson(element.get(0))), value.apply(new UnpackJson(element.get(1))));
    }
    return map;
  }

  @Override
  public Object object(Stream<Pair<String, Imyhat>> contents) {
    return new Tuple(
        contents
            .sorted(Comparator.comparing(Pair::first))
            .map(field -> field.second().apply(new UnpackJson(value.get(field.first()))))
            .toArray());
  }

  @Override
  public Object optional(Imyhat inner) {
    if (value == null || value.isNull()) {
      return Optional.empty();
    } else {
      return Optional.of(inner.apply(this));
    }
  }

  @Override
  public Object path() {
    return Paths.get(value.asText());
  }

  @Override
  public Object string() {
    return value.asText();
  }

  @Override
  public Object tuple(Stream<Imyhat> contents) {
    return new Tuple(
        contents
            .map(
                new Function<>() {
                  int index;

                  @Override
                  public Object apply(Imyhat type) {
                    return type.apply(new UnpackJson(value.get(index++)));
                  }
                })
            .toArray());
  }
}
