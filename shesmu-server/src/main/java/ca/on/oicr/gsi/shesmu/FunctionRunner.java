package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public interface FunctionRunner {
	public void run(ArrayNode parameter, ObjectNode output);
}
