package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Generates an action from parameters stored in a JSON blob */
public interface ActionRunner {
  Action run(ObjectNode parameters);
}
