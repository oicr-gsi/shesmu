package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import org.objectweb.asm.Type;

/** Build a grouping operation */
public interface Regrouper {

  /**
   * Add a new collection of values slurped during iteration
   *
   * @param valueType the type of the values in the collection
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addCollected(Imyhat valueType, String fieldName, Consumer<Renderer> loader);

  /**
   * Add a new dictionary of values slurped during iteration
   *
   * @param keyType the type of the keys in the dictionary
   * @param valueType
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addCollected(
      Imyhat keyType,
      Imyhat valueType,
      String fieldName,
      Consumer<Renderer> keyLoader,
      Consumer<Renderer> valueLoader);

  /**
   * Count the number of matching rows.
   *
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addCount(String fieldName);

  /**
   * A single value to collected by a matching row
   *
   * @param fieldType the type of the value being added
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addFirst(Type fieldType, String fieldName, Consumer<Renderer> loader);

  void addFirst(
      Type fieldType, String fieldName, Consumer<Renderer> loader, Consumer<Renderer> first);

  /**
   * Add a new collection of collections of values slurped during iteration
   *
   * @param valueType the type of the values in the collection
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addFlatten(Imyhat valueType, String fieldName, Consumer<Renderer> loader);

  /**
   * Add a value that is the Boolean result of checking expressions
   *
   * @param condition the condition that must be satisfied
   */
  void addMatches(String name, Match matchType, Consumer<Renderer> condition);

  /**
   * Effectively flatten for optionals
   *
   * <p>The row will be rejected if it has zero or 2 or more items.
   *
   * @param valueType the type of the values in the collection
   * @param fieldName the name of the output variable
   * @param loader a function to load the variable; this must return an optional
   */
  void addOnlyIf(Imyhat valueType, String fieldName, Consumer<Renderer> loader);

  /**
   * A single value which is the optima from all input values
   *
   * @param fieldType the type of the value being added
   * @param fieldName the name of the variable for consumption by downstream uses
   */
  void addOptima(Type fieldType, String fieldName, boolean max, Consumer<Renderer> loader);

  void addOptima(
      Type asmType,
      String name,
      boolean max,
      Consumer<Renderer> loader,
      Consumer<Renderer> initial);

  /**
   * Count whether a variable matches a condition
   *
   * @param fieldName the name of the variable for consumption by downstream uses
   * @param condition the condition that must be satisfied
   */
  void addPartitionCount(String fieldName, Consumer<Renderer> condition);

  /**
   * Create a collection that should only ever have one item in it
   *
   * <p>The row will be rejected if it has zero or 2 or more items.
   *
   * @param valueType the type of the values in the collection
   * @param fieldName the name of the output variable
   * @param loader a function to load the variable
   */
  void addUnivalued(Imyhat valueType, String fieldName, Consumer<Renderer> loader);

  void addUnivalued(
      Imyhat valueType,
      String fieldName,
      Consumer<Renderer> loader,
      Consumer<Renderer> defaultValue);

  /**
   * Conditionally add a variable
   *
   * @param condition the condition that must be satisfied
   */
  Regrouper addWhere(Consumer<Renderer> condition);
}
