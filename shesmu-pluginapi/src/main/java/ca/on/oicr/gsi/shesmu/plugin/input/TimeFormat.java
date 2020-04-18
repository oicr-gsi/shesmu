package ca.on.oicr.gsi.shesmu.plugin.input;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.time.Instant;

public enum TimeFormat {
  MILLIS_NUMERIC {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeNumber(value.toEpochMilli());
    }
  },
  SECONDS_NUMERIC {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeNumber(value.toEpochMilli() / 1e3);
    }
  },
  ISO8660_STRING {
    @Override
    public void write(JsonGenerator generator, Instant value) throws IOException {
      generator.writeString(value.toString());
    }
  };

  public abstract void write(JsonGenerator generator, Instant value) throws IOException;
}
