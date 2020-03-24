package ca.on.oicr.gsi.shesmu.plugin.types;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Converts a Shesmu data structure to a byte array.
 *
 * <p>This array isn't meant to be parsed. It is meant for generating signatures and UUIDs
 */
public abstract class ToBytesConverter implements ImyhatConsumer {
  @Override
  public final void accept(boolean value) {
    add(new byte[] {(byte) (value ? 1 : 0)});
  }

  @Override
  public final void accept(double value) {
    add(ByteBuffer.allocate(Double.BYTES).order(ByteOrder.BIG_ENDIAN).putDouble(value).array());
  }

  @Override
  public final void accept(Instant value) {
    add(new byte[] {(byte) 133});
    add(
        ByteBuffer.allocate(Long.BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(value.toEpochMilli())
            .array());
  }

  @Override
  public final void accept(long value) {
    add(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array());
  }

  @Override
  public final void accept(Path value) {
    add(new byte[] {(byte) 42});
    add(value.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public final void accept(Stream<Object> values, Imyhat inner) {
    add(new byte[] {(byte) 9});
    values.forEach(item -> inner.accept(this, item));
  }

  @Override
  public final void accept(String value) {
    add(value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public final void accept(JsonNode value) {
    try {
      add(value.binaryValue());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public final void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      add(new byte[] {(byte) 'q'});
      inner.accept(this, value.get());
    } else {
      add(new byte[] {(byte) 'Q'});
    }
  }

  @Override
  public final void acceptMap(Map<?, ?> map, Imyhat key, Imyhat value) {
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      key.accept(this, entry.getKey());
      add(new byte[] {(byte) 0});
      value.accept(this, entry.getValue());
      add(new byte[] {(byte) 0});
    }
  }

  @Override
  public final void acceptObject(Stream<Field<String>> fields) {
    fields.forEach(
        field -> {
          add(field.index().getBytes(StandardCharsets.UTF_8));
          add(new byte[] {(byte) '$'});
          field.type().accept(this, field.value());
        });
  }

  @Override
  public final void acceptTuple(Stream<Field<Integer>> fields) {
    fields.forEach(
        field -> {
          add(new byte[] {field.index().byteValue()});
          field.type().accept(this, field.value());
        });
  }

  /**
   * Add some new bytes to the growing signature
   *
   * <p>This may be called multiple times
   *
   * @param bytes the bytes to add
   */
  protected abstract void add(byte[] bytes);
}
