package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Parse a JSON value based on a type */
public class UnpackJson implements ImyhatTransformer<Object> {

  private final JsonNode value;

  public UnpackJson(JsonNode value) {
    this.value = value;
  }

  @Override
  public Object bool() {
    return value.asBoolean();
  }

  @Override
  public Object date() {
    if (value == null || value.isNull()) {
      return Instant.EPOCH;
    } else if (value.isLong()) {
      return Instant.ofEpochMilli(value.asLong());
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
  public Object list(Imyhat inner) {
    return IntStream.range(0, value.size())
        .mapToObj(i -> inner.apply(new UnpackJson(value.get(i))))
        .collect(inner.toSet());
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
      return inner.apply(this);
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
                new Function<Imyhat, Object>() {
                  int index;

                  @Override
                  public Object apply(Imyhat type) {
                    return type.apply(new UnpackJson(value.get(index++)));
                  }
                })
            .toArray());
  }
}
