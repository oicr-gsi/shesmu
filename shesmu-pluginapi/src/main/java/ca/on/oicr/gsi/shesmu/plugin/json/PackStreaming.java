package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

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
      timeFormat.write(generator, value);
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
  public void accept(JsonNode value) {
    try {
      generator.writeTree(value);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    try {
      if (key.isSame(Imyhat.STRING)) {
        generator.writeStartObject();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
          generator.writeFieldName((String) entry.getKey());
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
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      inner.accept(this, value.get());
    } else {
      try {
        generator.writeNull();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void accept(String name, Consumer<ImyhatConsumer> accessor) {
    try {
      generator.writeStartObject();
      generator.writeStringField("type", name);
      generator.writeObjectFieldStart("contents");
      accessor.accept(this);
      generator.writeEndObject();
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
              generator.writeFieldName(field.index());
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
