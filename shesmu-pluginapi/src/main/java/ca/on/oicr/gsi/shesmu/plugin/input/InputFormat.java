package ca.on.oicr.gsi.shesmu.plugin.input;

/**
 * Define a new input format
 *
 * <p>To use this class, create a subclass with a no arguments constructor and make it available as
 * a plugin
 */
public class InputFormat {
  private final String name;
  private final Class<?> clazz;

  /**
   * Create a new input format
   *
   * @param name the name for the input format; this must be a valid Shesmu identifier
   * @param clazz the type of each row in the input format. This class must not be generic.
   */
  public InputFormat(String name, Class<?> clazz) {
    if (clazz.getTypeParameters().length > 0) {
      throw new IllegalArgumentException(
          String.format(
              "Class %s has type parameters. It cannot be used in input format %s.", clazz, name));
    }
    this.name = name;
    this.clazz = clazz;
  }

  public final String name() {
    return name;
  }

  public final Class<?> type() {
    return clazz;
  }
}
