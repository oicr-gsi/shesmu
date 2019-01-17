package ca.on.oicr.gsi.shesmu.util.server;

import ca.on.oicr.gsi.shesmu.Action;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Generates an action from parameters stored in a JSON blob */
public interface ActionRunner {
  public Action run(ObjectNode parameters);
}
