package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JsonConverter implements ImyhatTransformer<Optional<Object>> {
  private final JsonNode input;

  public JsonConverter(JsonNode input) {
    this.input = input;
  }

  @Override
  public Optional<Object> algebraic(Stream<AlgebraicTransformer> contents) {
    if (!input.isObject()) {
      contents.close();
      return Optional.empty();
    }
    if (input.has("type") && input.get("type").isTextual() && input.has("contents")) {
      final String type = input.get("type").asText();
      final JsonNode arguments = input.get("contents");
      return contents
          .filter(t -> t.name().equals(type))
          .findFirst()
          .flatMap(
              t ->
                  t.visit(
                      new AlgebraicVisitor<Optional<Object>>() {
                        @Override
                        public Optional<Object> empty(String name) {
                          return arguments.isNull()
                                  || (arguments.isObject() || arguments.isArray())
                                      && arguments.isEmpty()
                              ? Optional.of(new AlgebraicValue(type))
                              : Optional.empty();
                        }

                        @Override
                        public Optional<Object> object(
                            String name, Stream<Pair<String, Imyhat>> contents) {
                          return JsonConverter.this.object(
                              arguments, contents, a -> new AlgebraicValue(name, a));
                        }

                        @Override
                        public Optional<Object> tuple(String name, Stream<Imyhat> contents) {
                          return JsonConverter.this.tuple(
                              arguments, contents, a -> new AlgebraicValue(name, a));
                        }
                      }));
    }
    contents.close();
    return Optional.empty();
  }

  @Override
  public Optional<Object> bool() {
    return input.isBoolean() ? Optional.of(input.asBoolean()) : Optional.empty();
  }

  @Override
  public Optional<Object> date() {
    return input.isIntegralNumber()
        ? Optional.of(Instant.ofEpochMilli(input.asLong()))
        : Optional.empty();
  }

  @Override
  public Optional<Object> floating() {
    return input.isNumber() ? Optional.of(input.asDouble()) : Optional.empty();
  }

  @Override
  public Optional<Object> integer() {
    return input.isIntegralNumber() ? Optional.of(input.asLong()) : Optional.empty();
  }

  @Override
  public Optional<Object> json() {
    return Optional.of(input);
  }

  @Override
  public Optional<Object> list(Imyhat inner) {
    if (!input.isArray()) {
      return Optional.empty();
    }
    final Set<Object> output = inner.newSet();
    for (final JsonNode element : input) {
      final Optional<Object> outputElement = inner.apply(new JsonConverter(element));
      if (outputElement.isPresent()) {
        outputElement.ifPresent(output::add);
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(output);
  }

  @Override
  public Optional<Object> map(Imyhat key, Imyhat value) {
    @SuppressWarnings("unchecked")
    final Comparator<Object> comparator = (Comparator<Object>) key.comparator();
    final SortedMap<Object, Object> map = new TreeMap<>(comparator);
    if (key.isSame(Imyhat.STRING) && input.isObject()) {
      final Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
      while (fields.hasNext()) {
        final Map.Entry<String, JsonNode> field = fields.next();
        final Optional<Object> result = value.apply(new JsonConverter(field.getValue()));
        if (!result.isPresent()) {
          return Optional.empty();
        }
        map.put(field.getKey(), result.get());
      }
      return Optional.of(map);
    }
    if (!input.isArray()) {
      return Optional.empty();
    }
    for (final JsonNode element : input) {
      if (!element.isArray() || element.size() != 2) {
        return Optional.empty();
      }
      final Optional<Object> keyResult = key.apply(new JsonConverter(element.get(0)));
      final Optional<Object> valueResult = value.apply(new JsonConverter(element.get(1)));
      if (keyResult.isPresent() && valueResult.isPresent()) {
        map.put(keyResult.get(), valueResult.get());
      } else {
        return Optional.empty();
      }
    }
    return Optional.of(map);
  }

  @Override
  public Optional<Object> object(Stream<Pair<String, Imyhat>> contents) {
    return object(input, contents, Tuple::new);
  }

  private Optional<Object> object(
      JsonNode input,
      Stream<Pair<String, Imyhat>> contents,
      Function<Object[], Object> constructor) {
    if (!input.isObject()) {
      contents.close();
      return Optional.empty();
    }
    final Object[] output = new Object[input.size()];
    if (contents.allMatch(
        new Predicate<Pair<String, Imyhat>>() {
          private int index;

          @Override
          public boolean test(Pair<String, Imyhat> field) {
            if (!input.has(field.first())) {
              return false;
            }
            final Optional<Object> element =
                field.second().apply(new JsonConverter(input.get(field.first())));
            element.ifPresent(v -> output[index++] = v);
            return element.isPresent();
          }
        })) {
      return Optional.of(constructor.apply(output));
    }
    return Optional.empty();
  }

  @Override
  public Optional<Object> optional(Imyhat inner) {
    return input.isNumber() ? Optional.empty() : inner.apply(this);
  }

  @Override
  public Optional<Object> path() {
    return input.isTextual() ? Optional.of(Paths.get(input.asText())) : Optional.empty();
  }

  @Override
  public Optional<Object> string() {
    return input.isTextual() ? Optional.of(input.asText()) : Optional.empty();
  }

  @Override
  public Optional<Object> tuple(Stream<Imyhat> contents) {
    return tuple(input, contents, Tuple::new);
  }

  private Optional<Object> tuple(
      JsonNode input, Stream<Imyhat> contents, Function<Object[], Object> constructor) {
    if (!input.isArray()) {
      contents.close();
      return Optional.empty();
    }
    final Object[] output = new Object[input.size()];
    if (contents.allMatch(
        new Predicate<Imyhat>() {
          private int index;

          @Override
          public boolean test(Imyhat imyhat) {
            if (input.size() >= index) {
              return false;
            }
            final Optional<Object> element = imyhat.apply(new JsonConverter(input.get(index)));
            element.ifPresent(v -> output[index] = v);
            index++;
            return element.isPresent();
          }
        })) {
      return Optional.of(constructor.apply(output));
    }
    return Optional.empty();
  }
}
