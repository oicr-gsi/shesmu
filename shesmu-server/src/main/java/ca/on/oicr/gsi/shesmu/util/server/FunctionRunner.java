package ca.on.oicr.gsi.shesmu.util.server;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;

/**
 * A wrapper produced for a {@link FunctionDefinition} that can be run from the
 * REST interface
 */
public interface FunctionRunner {
	public void run(ArrayNode parameter, ObjectNode output);
}
