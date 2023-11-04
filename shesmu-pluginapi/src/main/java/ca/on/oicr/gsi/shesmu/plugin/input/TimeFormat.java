package ca.on.oicr.gsi.shesmu.plugin.input;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.time.Instant;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public enum TimeFormat {
  MILLIS_NUMERIC {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeNumber(value.toEpochMilli());
    }

    @Override
    public void write(XMLStreamWriter writer, Instant value) throws XMLStreamException {
      writer.writeCharacters(Long.toString(value.toEpochMilli()));
    }
  },
  SECONDS_NUMERIC {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeNumber(value.toEpochMilli() / 1e3);
    }

    @Override
    public void write(XMLStreamWriter writer, Instant value) throws XMLStreamException {
      writer.writeCharacters(Double.toString(value.toEpochMilli() / 1e3));
    }
  },
  ISO8660_STRING {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeString(value.toString());
    }

    @Override
    public void write(XMLStreamWriter writer, Instant value) throws XMLStreamException {
      writer.writeCharacters(value.toString());
    }
  };

  public abstract void write(JsonGenerator generator, Instant value) throws IOException;

  public abstract void write(XMLStreamWriter writer, Instant value) throws XMLStreamException;
}
