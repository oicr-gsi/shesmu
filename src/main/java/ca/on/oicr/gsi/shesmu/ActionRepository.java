package ca.on.oicr.gsi.shesmu;

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
	 * @return a pair containing the name of the action and a callback to generate a
	 *         matching action builder. The callback may be invoked multiple times
	 *         and should return a fresh instance every time.
	 */
	Stream<ActionDefinition> query();

}
