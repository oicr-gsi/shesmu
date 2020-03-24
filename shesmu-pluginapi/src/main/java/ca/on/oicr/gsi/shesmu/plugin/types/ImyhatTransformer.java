package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import java.util.stream.Stream;

/**
 * Convert a Shesmu type description into another value
 *
 * <p>This is meant to transform a description of a Shesmu type into another representation (e.g., a
 * string containing a WDL type)
 *
 * @param <R> the return type to be generated
 */
public interface ImyhatTransformer<R> {

  /** Convert a <tt>boolean</tt> type */
  R bool();

  /** Convert a <tt>date</tt> type */
  R date();

  /** Convert a <tt>float</tt> type */
  R floating();

  /** Convert an <tt>integer</tt> type */
  R integer();

  /** Convert a <tt>json</tt> type */
  R json();

  /**
   * Convert a list type
   *
   * @param inner the type of the contents of the list
   */
  R list(Imyhat inner);

  /**
   * Convert a map type
   *
   * @param key the type of the keys
   * @param value the type of the values
   */
  R map(Imyhat key, Imyhat value);

  /**
   * Convert an object type
   *
   * @param contents a list of fields in the object and their types
   */
  R object(Stream<Pair<String, Imyhat>> contents);

  /**
   * Convert an optional type
   *
   * @param inner the type inside the optional; may be null
   */
  R optional(Imyhat inner);

  /** Convert a <tt>path</tt> type */
  R path();

  /** Convert a <tt>string</tt> type */
  R string();

  /**
   * Convert a tuple type
   *
   * @param contents the types of the items in the tuple, in order
   */
  R tuple(Stream<Imyhat> contents);
}
