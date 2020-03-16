package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * Collapse the nested structure of Shesmu objects down to the output handlers that know how to deal
 * with it
 */
class PackWdlOutputs implements ImyhatConsumer {
  private final BiConsumer<String, CustomLimsEntry> result;
  private final Map<String, CustomLimsTransformer> handlers;

  PackWdlOutputs(
      BiConsumer<String, CustomLimsEntry> result, Map<String, CustomLimsTransformer> handlers) {
    this.result = result;
    this.handlers = handlers;
  }

  @Override
  public void accept(boolean value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(double value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Instant value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Path value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Stream<Object> values, Imyhat inner) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(String value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(JsonNode value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(Imyhat inner, Optional<?> value) {
    value.ifPresent(o -> inner.accept(this, o));
  }

  @Override
  public void acceptObject(Stream<Field<String>> fields) {
    fields.forEach(f -> handlers.get(f.index()).write(f.type(), f.value(), result));
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    throw new UnsupportedOperationException();
  }
}
