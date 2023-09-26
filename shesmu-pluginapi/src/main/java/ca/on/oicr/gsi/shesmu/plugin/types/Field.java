package ca.on.oicr.gsi.shesmu.plugin.types;

/**
 * The field inside a tuple or object
 *
 * @param <I> the type of index used (number for tuples, strings for objects)
 */
public class Field<I> {
  private final I index;
  private final Imyhat type;
  private final Object value;

  /**
   * Construct a new field instance
   *
   * @param index the field identifier/index
   * @param value the field value
   * @param type the field type
   */
  public Field(I index, Object value, Imyhat type) {
    super();
    this.index = index;
    this.value = value;
    this.type = type;
  }

  /**
   * Get the identifier of this field in the containing object/tuple
   *
   * @return the index
   */
  public I index() {
    return index;
  }

  /**
   * Get the type of this field
   *
   * @return the type descriptor
   */
  public Imyhat type() {
    return type;
  }

  /**
   * Get the value of this field
   *
   * @return the field value
   */
  public Object value() {
    return value;
  }
}
