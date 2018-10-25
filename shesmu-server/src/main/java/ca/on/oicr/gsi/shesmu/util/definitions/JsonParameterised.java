package ca.on.oicr.gsi.shesmu.util.definitions;

import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;

/**
 * An {@link Action} that stores some of its parameters in a JSON object
 */
public interface JsonParameterised {
	/**
	 * The JSON object to mutate when writing parameters.
	 *
	 * This must not return null.
	 */
	public ObjectNode parameters();
}
