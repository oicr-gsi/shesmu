package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Convert a value based on the provided information
 *
 * <p>In some cases, a Shesmu value may be passed to Java as {@link Object} and an accompanying
 * {@link Imyhat}. This interface allows converting the value into more specific values and
 * returning a common result.
 *
 * @param <R> the output type that will result from the conversion
 * @see Imyhat#apply(ImyhatFunction, Object)
 */
public interface ImyhatFunction<R> {

  /** Convert a Boolean value */
  R apply(boolean value);

  /** Convert a floating point value */
  R apply(double value);
  /** Convert a date value */
  R apply(Instant value);

  /** Convert an integral value */
  R apply(long value);

  /**
   * Convert a list of items
   *
   * @param values the values in the list
   * @param inner the type of the values in the list
   */
  R apply(Stream<Object> values, Imyhat inner);

  /** Convert a string value */
  R apply(String value);

  /** Convert a path value */
  R apply(Path value);

  /**
   * Convert a tuple
   *
   * @param contents the values, types, and indices of items in the tuple
   */
  R applyTuple(Stream<Field<Integer>> contents);

  /**
   * Convert a object
   *
   * @param contents the values, types, and field names of items in the object
   */
  R applyObject(Stream<Field<String>> contents);
}
