package ca.on.oicr.gsi.shesmu.plugin.input;

/** Add a variable to a variable gang */
public @interface Gang {
  /**
   * The name of the group
   *
   * @return the name, which must be a valid Shesmu identifier
   */
  String name();

  /**
   * The order a variable should be in the group
   *
   * <p>This only matters if the group is converted to a string
   *
   * @return the position
   */
  int order();

  /**
   * When creating a string, exclude this variable (and its separator) if it has a default value
   * (<em>e.g.</em>, empty string, zero)
   *
   * @return true if omitted when default
   */
  boolean dropIfDefault() default false;
}
