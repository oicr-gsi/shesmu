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

  public Field(I index, Object value, Imyhat type) {
    super();
    this.index = index;
    this.value = value;
    this.type = type;
  }

  /** Get the identifier of this field in the containing object/tuple */
  public I index() {
    return index;
  }

  /** Get the type of this field */
  public Imyhat type() {
    return type;
  }

  /** Get the value of this field */
  public Object value() {
    return value;
  }
}
