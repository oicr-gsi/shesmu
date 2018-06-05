package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Generates an action from parameters stored in a JSON blob
 */
public interface ActionRunner {
	public Action run(ObjectNode parameters);
}
