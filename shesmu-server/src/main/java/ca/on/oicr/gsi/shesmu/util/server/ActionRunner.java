package ca.on.oicr.gsi.shesmu.util.server;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;

/**
 * Generates an action from parameters stored in a JSON blob
 */
public interface ActionRunner {
	public Action run(ObjectNode parameters);
}
