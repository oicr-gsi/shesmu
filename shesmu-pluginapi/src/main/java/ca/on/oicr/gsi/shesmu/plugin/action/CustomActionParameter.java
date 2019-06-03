package ca.on.oicr.gsi.shesmu.plugin.action;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/** A definition for a parameter to an action */
public abstract class CustomActionParameter<A extends Action> {

  private final String name;
  private final boolean required;
  private final Imyhat type;

  public CustomActionParameter(String name, boolean required, Imyhat type) {
    super();
    this.name = name;
    this.required = required;
    this.type = type;
  }

  /** The name of the parameter as the user will set it. */
  public final String name() {
    return name;
  }

  /**
   * Whether this parameter is required or not.
   *
   * <p>If not required, the user may omit setting the value.
   */
  public final boolean required() {
    return required;
  }

  /** Store the value in an action */
  public abstract void store(A action, Object value);

  /** The type of the parameter */
  public final Imyhat type() {
    return type;
  }
}
