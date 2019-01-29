package ca.on.oicr.gsi.shesmu.plugin.functions;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/** Description of a parameter to a function */
public final class FunctionParameter {

  private final String description;

  private final Imyhat type;

  /**
   * Create a new function parameter
   *
   * @param description the help text for this parameter
   * @param type the Shesmu type for this paramter
   */
  public FunctionParameter(String description, Imyhat type) {
    super();
    this.description = description;
    this.type = type;
  }

  /** Help text for this parameter */
  public String description() {
    return description;
  }

  /** The type of this parameter */
  public Imyhat type() {
    return type;
  }
}
