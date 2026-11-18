package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;

/** Convert a value to JSON using the streaming interface */
public class PackStreaming implements ImyhatConsumer {
  private final JsonGenerator generator;
  private final TimeFormat timeFormat;

  public PackStreaming(JsonGenerator generator, TimeFormat timeFormat) {
    super();
    this.generator = generator;
    this.timeFormat = timeFormat;
  }

  @Override
  public void accept(boolean value) {
    generator.writeBoolean(value);
  }

  @Override
  public void accept(double value) {
    generator.writeNumber(value);
  }

  @Override
  public void accept(Instant value) {
    try {
      timeFormat.write(generator, value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(long value) {
    generator.writeNumber(value);
  }

  @Override
  public void accept(Path value) {
    generator.writeString(value.toString());
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    generator.writeStartArray();
    values.forEach(value -> inner.accept(this, value));
    generator.writeEndArray();
  }

  @Override
  public void accept(String value) {
    generator.writeString(value);
  }

  @Override
  public void accept(JsonNode value) {
    generator.writeTree(value);
  }

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    if (key.isSame(Imyhat.STRING)) {
      generator.writeStartObject();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        generator.writeName((String) entry.getKey());
        value.accept(this, entry.getValue());
      }
      generator.writeEndObject();

    } else {
      generator.writeStartArray();
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        generator.writeStartArray();
        key.accept(this, entry.getKey());
        value.accept(this, entry.getValue());
        generator.writeEndArray();
      }
      generator.writeEndArray();
    }
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      inner.accept(this, value.get());
    } else {
      generator.writeNull();
    }
  }

  @Override
  public void accept(String name, Consumer<ImyhatConsumer> accessor) {
    generator.writeStartObject();
    generator.writeStringProperty("type", name);
    generator.writeName("contents");
    accessor.accept(this);
    generator.writeEndObject();
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    generator.writeStartObject();
    fields.forEach(
        field -> {
          generator.writeName(field.index());
          field.type().accept(this, field.value());
        });
    generator.writeEndObject();
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    generator.writeStartArray();
    fields
        .sorted(Comparator.comparingInt(Field::index))
        .forEach(field -> field.type().accept(this, field.value()));
    generator.writeEndArray();
  }
}
