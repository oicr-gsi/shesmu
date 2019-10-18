package ca.on.oicr.gsi.shesmu.plugin.input;

/** Add a variable to a variable gang */
public @interface Gang {
  /** The name of the group */
  String name();

  /**
   * The order a variable should be in the group
   *
   * <p>This only matters if the group is converted to a string
   */
  int order();

  /**
   * When creating a string, exclude this variable (and its separator) if it has a default value
   * (e.g., empty string, zero)
   */
  boolean dropIfDefault() default false;
}
