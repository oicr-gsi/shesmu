package ca.on.oicr.gsi.shesmu;

/** Describe the current completeness of an action */
public enum ActionState {
  /** The action has been attempted and encounter an error (possibly recoverable). */
  FAILED(3),
  /** The action is currently being executed. */
  INFLIGHT(2),
  /** The action is waiting for a remote system to start it. */
  QUEUED(2),
  /** The action is complete */
  SUCCEEDED(1),
  /**
   * The action is being rate limited by a {@link Throttler} or by an over-capacity signal from the
   * remote system.
   */
  THROTTLED(2),
  /**
   * The actions state is not currently known either due to an error or not having been attempted
   */
  UNKNOWN(2),
  /**
   * The action cannot be started due to a resource being unavailable
   *
   * <p>This is slightly different from {@link #THROTTLED}, which indicates this action could be run
   * if there were capacity, while this indicates that the action can't be run right now even if
   * capacity is available. This might be due to needing another action to complete or requiring
   * user intervention.
   */
  WAITING(2);

  private final int sortPriority;

  private ActionState(int sortPriority) {
    this.sortPriority = sortPriority;
  }

  public int sortPriority() {
    return sortPriority;
  }
}
