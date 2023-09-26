package ca.on.oicr.gsi.shesmu.plugin.types;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

  interface AccessContents {
    <R> R apply(ImyhatFunction<R> function);
  }

  /**
   * Convert an algebraic type
   *
   * @param name the algebraic type tag
   * @param accessor a function which access the contents of the algebraic type
   * @return the converted value
   */
  R apply(String name, AccessContents accessor);

  /**
   * Convert a Boolean value
   *
   * @param value the Boolean value to convert
   * @return the converted value
   */
  R apply(boolean value);

  /**
   * Convert a floating point value
   *
   * @param value the double value to convert
   * @return the converted value
   */
  R apply(double value);

  /**
   * Convert a date value
   *
   * @param value the date value to convert
   * @return the converted value
   */
  R apply(Instant value);

  /**
   * Convert an integral value
   *
   * @param value the long value to convert
   * @return the converted value
   */
  R apply(long value);

  /**
   * Convert a list of items
   *
   * @param values the values in the list
   * @param inner the type of the values in the list
   * @return the converted value
   */
  R apply(Stream<Object> values, Imyhat inner);

  /**
   * Convert a string value
   *
   * @param value the string to convert
   * @return the converted value
   */
  R apply(String value);

  /**
   * Convert a path value
   *
   * @param value the path to convert
   * @return the converted value
   */
  R apply(Path value);

  /**
   * Convert an empty optional
   *
   * @param inner the type inside the optional
   * @param value the optional to convert
   * @return the converted value
   */
  R apply(Imyhat inner, Optional<?> value);

  /**
   * Convert a JSON value
   *
   * @param value the JSON value to convert
   * @return the converted value
   */
  R apply(JsonNode value);
  /**
   * Collect a map type
   *
   * @param map the map to convert
   * @param key the type of the map key
   * @param value the type of the map value
   * @return the converted value
   */
  R applyMap(Map<?, ?> map, Imyhat key, Imyhat value);

  /**
   * Convert a object
   *
   * @param contents the values, types, and field names of items in the object
   * @return the converted value
   */
  R applyObject(Stream<Field<String>> contents);

  /**
   * Convert a tuple
   *
   * @param contents the values, types, and indices of items in the tuple
   * @return the converted value
   */
  R applyTuple(Stream<Field<Integer>> contents);
}
