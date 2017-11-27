package ca.on.oicr.gsi.shesmu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * An action that can be performed as a result of the decision-making process
 *
 * This is the information needed to perform an action. Creating this action
 * should not perform it until {{@link #perform()} is called. The action should
 * be connected to some kind of stateful remote system to check if the action
 * has been performed and track its status.
 */
public abstract class Action {
	@Override
	public abstract boolean equals(Object other);

	@Override
	public abstract int hashCode();

	/**
	 * Perform this action.
	 *
	 * This will be called multiple times, so if the action needs to be performed,
	 * it should be launched. The status of previously launched copies should be
	 * reported. This object may be recreated since the launch, so the action cannot
	 * expect to hold permanent state (e.g., job id) as a field.
	 */
	public abstract ActionState perform();

	/**
	 * The number of minutes to wait before attempting to retry this action.
	 *
	 * This method should be O(1).
	 */
	public abstract long retryMinutes();

	/**
	 * Render the action to JSON for display by the front end.
	 */
	public abstract ObjectNode toJson(ObjectMapper mapper);

}
