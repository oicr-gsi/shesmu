package ca.on.oicr.gsi.shesmu.core.signers;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.signature.DynamicSigner;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public class SHA1DigestSigner implements DynamicSigner<String>, ImyhatConsumer {
  private final MessageDigest digest;

  public SHA1DigestSigner() throws NoSuchAlgorithmException {
    digest = MessageDigest.getInstance("SHA1");
  }

  @Override
  public void accept(boolean value) {
    digest.update((byte) (value ? 1 : 0));
  }

  @Override
  public void accept(double value) {
    digest.update(
        ByteBuffer.allocate(Double.BYTES).order(ByteOrder.BIG_ENDIAN).putDouble(value).array());
  }

  @Override
  public void accept(Instant value) {
    digest.update((byte) 133);
    digest.update(
        ByteBuffer.allocate(Long.BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(value.toEpochMilli())
            .array());
  }

  @Override
  public void accept(long value) {
    digest.update(
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array());
  }

  @Override
  public void accept(Path value) {
    digest.update((byte) 42);
    digest.update(value.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    digest.update((byte) 9);
    values.forEach(item -> inner.accept(this, item));
  }

  @Override
  public void accept(String value) {
    digest.update(value.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    fields.forEach(
        field -> {
          digest.update(field.index().getBytes(StandardCharsets.UTF_8));
          digest.update((byte) '$');
          field.type().accept(this, field.value());
        });
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    fields.forEach(
        field -> {
          digest.update(field.index().byteValue());
          field.type().accept(this, field.value());
        });
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    if (value.isPresent()) {
      digest.update((byte) 'q');
      inner.accept(this, value.get());
    } else {
      digest.update((byte) 'Q');
    }
  }

  @Override
  public void addVariable(String name, Imyhat type, Object value) {
    digest.update(name.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) ':');
    type.accept(this, value);
  }

  @Override
  public String finish() {
    return Utils.bytesToHex(digest.digest());
  }
}
