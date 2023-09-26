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

  /** Transform an algebraic type */
  interface AlgebraicTransformer {

    /**
     * The algebraic tag
     *
     * @return the identifier for this entry
     */
    String name();

    /**
     * Transform the type inside the algebraic type
     *
     * @param visitor a visitor to process the inner type
     * @return the transformation result
     * @param <R> the result type
     */
    <R> R visit(AlgebraicVisitor<R> visitor);
  }

  /**
   * Convert an algebraic type
   *
   * @param <R> the result type after transformation
   */
  interface AlgebraicVisitor<R> {

    /**
     * Convert an empty algebraic type
     *
     * @param name the type tag of the type
     * @return the transformed result
     */
    R empty(String name);

    /**
     * Convert an object algebraic type
     *
     * @param name the type tag of the type
     * @param contents a list of fields in the object and their types
     * @return the transformed result
     */
    R object(String name, Stream<Pair<String, Imyhat>> contents);

    /**
     * Convert a tuple algebraic type
     *
     * @param name the type tag of the type
     * @param contents the types of the items in the tuple, in order
     * @return the transformed result
     */
    R tuple(String name, Stream<Imyhat> contents);
  }

  /**
   * Convert an algebraic type
   *
   * @param contents the different types that are permitted in this algebraic type
   * @return the transformed result
   */
  R algebraic(Stream<AlgebraicTransformer> contents);

  /**
   * Convert a <code>boolean</code> type
   *
   * @return the transformed result
   */
  R bool();

  /**
   * Convert a <code>date</code> type
   *
   * @return the transformed result
   */
  R date();

  /**
   * Convert a <code>float</code> type
   *
   * @return the transformed result
   */
  R floating();

  /**
   * Convert an <code>integer</code> type
   *
   * @return the transformed result
   */
  R integer();

  /**
   * Convert a <code>json</code> type
   *
   * @return the transformed result
   */
  R json();

  /**
   * Convert a list type
   *
   * @param inner the type of the contents of the list
   * @return the transformed result
   */
  R list(Imyhat inner);

  /**
   * Convert a map type
   *
   * @param key the type of the keys
   * @param value the type of the values
   * @return the transformed result
   */
  R map(Imyhat key, Imyhat value);

  /**
   * Convert an object type
   *
   * @param contents a list of fields in the object and their types
   * @return the transformed result
   */
  R object(Stream<Pair<String, Imyhat>> contents);

  /**
   * Convert an optional type
   *
   * @param inner the type inside the optional; may be null
   * @return the transformed result
   */
  R optional(Imyhat inner);

  /**
   * Convert a <code>path</code> type
   *
   * @return the transformed result
   */
  R path();

  /**
   * Convert a <code>string</code> type
   *
   * @return the transformed result
   */
  R string();

  /**
   * Convert a tuple type
   *
   * @param contents the types of the items in the tuple, in order
   * @return the transformed result
   */
  R tuple(Stream<Imyhat> contents);
}
