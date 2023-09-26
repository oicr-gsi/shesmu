package ca.on.oicr.gsi.shesmu.plugin.action;

/** Describe the current completeness of an action */
public enum ActionState {
  /** The action has been attempted and encounter an error (possibly recoverable). */
  FAILED(3, 2),
  /**
   * The action is in a state where it needs human attention or intervention to correct itself.
   *
   * <p>Usually, this means that the action has tried to recover state and found itself in an
   * inconsistent state that it can't recover from without doing something dangerous.
   */
  HALP(2, 2),
  /** The action is currently being executed. */
  INFLIGHT(2, 1),
  /** The action is waiting for a remote system to start it. */
  QUEUED(2, 1),
  /** The action has encountered some user-defined limit stopping it from proceeding. */
  SAFETY_LIMIT_REACHED(2, 2),
  /** The action is complete. */
  SUCCEEDED(1, 2),
  /**
   * The action is being rate limited by a {@link ActionServices#isOverloaded(String...)} or by an
   * over-capacity signal from the remote system.
   */
  THROTTLED(2, 2),
  /**
   * The actions state is not currently known either due to an error or not having been attempted
   */
  UNKNOWN(2, 2),
  /**
   * The action cannot be started due to a resource being unavailable
   *
   * <p>This is slightly different from {@link #THROTTLED}, which indicates this action could be run
   * if there were capacity, while this indicates that the action can't be run right now even if
   * capacity is available. This might be due to needing another action to complete or requiring
   * user intervention.
   */
  WAITING(2, 2),
  /**
   * The action is never going to complete. This is not necessarily a failed state; testing or
   * debugging actions should be in this state.
   *
   * <p>This is similar to {@link #SUCCEEDED} in that the action will never be checked again, but it
   * didn't really succeed. More reached a state of terminal stuckness.
   */
  ZOMBIE(1, 2);

  /**
   * sortPriority: available to plugins and the server for sorting Actions by type <br>
   * processPriority: used by the server to prioritize checking the status of certain types of
   * actions first, as having up-to-date information from a remote server on these actions is more
   * important.
   *
   * <p>Both are intended to be used by Comparators, so lower values indicate higher priority. Both
   * are separate from an individual Action's priority value.
   */
  private final int sortPriority, processPriority;

  ActionState(int sortPriority, int processPriority) {
    this.sortPriority = sortPriority;
    this.processPriority = processPriority;
  }

  /**
   * The priority of an action state where the largest number indicates the most miserable action
   *
   * @return an integer indicating relative misery
   */
  public int sortPriority() {
    return sortPriority;
  }

  /**
   * The priority of an action state for polling
   *
   * @return an integer indicating relative processing urgency
   */
  public int processPriority() {
    return processPriority;
  }
}
