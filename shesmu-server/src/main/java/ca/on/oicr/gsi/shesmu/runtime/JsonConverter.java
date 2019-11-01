package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class JsonConverter implements ImyhatTransformer<Optional<Object>> {
  private final JsonNode input;

  public JsonConverter(JsonNode input) {
    this.input = input;
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
  public Optional<Object> object(Stream<Pair<String, Imyhat>> contents) {
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
      return Optional.of(new Tuple(output));
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
      return Optional.of(new Tuple(output));
    }
    return Optional.empty();
  }
}
