package ca.on.oicr.gsi.shesmu.plugin.json;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.JsonParameterisedAction;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/**
 * A parameter definition for a parameter in a JSON object
 *
 * <p>This requires that the {@link Action} extends {@link JsonParameterisedAction} and will set a
 * property in that JSON object.
 */
public final class JsonParameter<A extends JsonParameterisedAction>
    extends CustomActionParameter<A, Object> {

  /**
   * The name of the JSON field
   *
   * @param name the JSON property name
   * @param type the type to use
   * @param required whether this parameter is required
   */
  public JsonParameter(String name, boolean required, Imyhat type) {
    super(name, required, type);
  }

  @Override
  public void store(JsonParameterisedAction action, Object value) {
    type().accept(new PackJsonObject(action.parameters(), name()), value);
  }
}
