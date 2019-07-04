package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

/** Convert a value to JSON using the streaming interface */
public class PackStreaming implements ImyhatConsumer {
  private final JsonGenerator generator;

  public PackStreaming(JsonGenerator generator) {
    super();
    this.generator = generator;
  }

  @Override
  public void accept(boolean value) {
    try {
      generator.writeBoolean(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(double value) {
    try {
      generator.writeNumber(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Instant value) {
    try {
      generator.writeNumber(value.toEpochMilli());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(long value) {
    try {
      generator.writeNumber(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Path value) {
    try {
      generator.writeString(value.toString());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    try {
      generator.writeStartArray();
      values.forEach(value -> inner.accept(this, value));
      generator.writeEndArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(String value) {
    try {
      generator.writeString(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    try {
      generator.writeStartObject();
      fields.forEach(
          field -> {
            try {
              generator.writeObjectFieldStart(field.index());
            } catch (IOException e) {
              throw new RuntimeException();
            }
            field.type().accept(this, field.value());
          });
      generator.writeEndObject();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    try {
      generator.writeStartArray();
      fields
          .sorted(Comparator.comparingInt(Field::index))
          .forEach(field -> field.type().accept(this, field.value()));
      generator.writeEndArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
