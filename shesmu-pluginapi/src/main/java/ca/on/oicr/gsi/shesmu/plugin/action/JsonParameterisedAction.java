package ca.on.oicr.gsi.shesmu.plugin.action;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** An {@link Action} that stores some of its parameters in a JSON object */
public abstract class JsonParameterisedAction extends Action {
  public JsonParameterisedAction(String type) {
    super(type);
  }

  /**
   * The JSON object to mutate when writing parameters.
   *
   * <p>This must not return null.
   */
  public abstract ObjectNode parameters();
}
