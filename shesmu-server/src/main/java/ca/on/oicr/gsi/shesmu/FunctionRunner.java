package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A wrapper produced for a {@link FunctionDefinition} that can be run from the
 * REST interface
 */
public interface FunctionRunner {
	public void run(ArrayNode parameter, ObjectNode output);
}
