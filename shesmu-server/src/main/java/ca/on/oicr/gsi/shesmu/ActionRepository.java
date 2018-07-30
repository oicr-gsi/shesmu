package ca.on.oicr.gsi.shesmu;

import java.io.PrintStream;
import java.util.stream.Stream;

/**
 * Provide a list of actions that can be performed by user-defined programs
 */
public interface ActionRepository extends LoadedConfiguration {

	/**
	 * Get the known actions
	 *
	 * This can be updated over time.
	 *
	 * @return a stream of definitions for actions the compiler can use
	 */
	Stream<ActionDefinition> queryActions();

	/**
	 * Write all the JavaScript code needed to pretty print this action.
	 */
	void writeJavaScriptRenderer(PrintStream writer);

}
