package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/** Convert a value to XML using the streaming interface */
public class PackStreamingXml implements ImyhatConsumer {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final XMLStreamWriter writer;
  private final TimeFormat timeFormat;

  public PackStreamingXml(XMLStreamWriter writer, TimeFormat timeFormat) {
    super();
    this.writer = writer;
    this.timeFormat = timeFormat;
  }

  @Override
  public void accept(boolean value) {
    try {
      writer.writeCharacters(Boolean.toString(value));
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(double value) {
    try {
      writer.writeCharacters(Double.toString(value));
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Instant value) {
    try {
      timeFormat.write(writer, value);
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(long value) {
    try {
      writer.writeCharacters(Long.toString(value));
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Path value) {
    try {
      writer.writeCharacters(value.toString());
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    try {
      writer.writeStartElement("list");
      values.forEach(
          value -> {
            try {
              writer.writeStartElement("item");
              inner.accept(this, value);
              writer.writeEndElement();
            } catch (XMLStreamException e) {
              throw new RuntimeException(e);
            }
          });
      writer.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(String value) {
    try {
      writer.writeCharacters(value);
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(JsonNode value) {
    try {
      writer.writeCData(MAPPER.writeValueAsString(value));
    } catch (XMLStreamException | JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    try {
      writer.writeStartElement("map");
      for (final Map.Entry<?, ?> entry : map.entrySet()) {
        writer.writeStartElement("entry");
        writer.writeStartElement("key");
        key.accept(this, entry.getKey());
        writer.writeEndElement();
        writer.writeStartElement("value");
        value.accept(this, entry.getValue());
        writer.writeEndElement();
        writer.writeEndElement();
      }
      writer.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    value.ifPresent(o -> inner.accept(this, o));
  }

  @Override
  public void accept(String name, Consumer<ImyhatConsumer> accessor) {
    try {
      writer.writeStartElement(name);
      accessor.accept(this);
      writer.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    try {
      writer.writeStartElement("object");
      fields.forEach(
          field -> {
            try {
              writer.writeStartElement(field.index());
              field.type().accept(this, field.value());
              writer.writeEndElement();
            } catch (XMLStreamException e) {
              throw new RuntimeException();
            }
          });
      writer.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    try {
      writer.writeStartElement("tuple");
      fields
          .sorted(Comparator.comparingInt(Field::index))
          .forEach(
              field -> {
                try {
                  writer.writeStartElement("element");
                  writer.writeAttribute("index", field.index().toString());
                  field.type().accept(this, field.value());
                  writer.writeEndElement();
                } catch (XMLStreamException e) {
                  throw new RuntimeException(e);
                }
              });
      writer.writeEndElement();
    } catch (XMLStreamException e) {
      throw new RuntimeException(e);
    }
  }
}
