package ca.on.oicr.gsi.shesmu.util.definitions;

import ca.on.oicr.gsi.shesmu.Action;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** An {@link Action} that stores some of its parameters in a JSON object */
public interface JsonParameterised {
  /**
   * The JSON object to mutate when writing parameters.
   *
   * <p>This must not return null.
   */
  public ObjectNode parameters();
}
