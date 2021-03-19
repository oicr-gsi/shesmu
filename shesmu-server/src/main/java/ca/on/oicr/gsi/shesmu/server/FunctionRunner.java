package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A wrapper produced for a {@link FunctionDefinition} that can be run from the REST interface */
public interface FunctionRunner {
  void run(ArrayNode parameter, ObjectNode output);
}
