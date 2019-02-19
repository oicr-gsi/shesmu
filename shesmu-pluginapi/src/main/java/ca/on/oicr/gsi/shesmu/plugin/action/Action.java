package ca.on.oicr.gsi.shesmu.plugin.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.regex.Pattern;

/**
 * An action that can be performed as a result of the decision-making process
 *
 * <p>This is the information needed to perform an action. As a Shesmu program operates, it will
 * instantiate new actions using and then put them in a set. If the same action is create multiple
 * times, it should be de-duplicated using {@link #equals(Object)} by the set. It will then attempt
 * to complete each action and track the success of performing an action.
 *
 * <p>Creating this action should not perform it until {{@link #perform(ActionServices)} is called.
 * If the action is needs to be in contact with a remote system, that information must be baked into
 * its constructor.
 */
public abstract class Action {
  private final String type;

  public Action(String type) {
    super();
    this.type = type;
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  /**
   * Attempt to complete this action.
   *
   * <p>This will be called multiple times until an action returns {@link ActionState#SUCCEEDED}.
   * Because Shesmu is stateless, the action must determine if an equivalent action has already been
   * performed. This object may be recreated since the launch, so the action cannot expect to hold
   * permanent state (e.g., job id) as a field.
   */
  public abstract ActionState perform(ActionServices services);

  /** Perform any preparation needed after parameters have been set. */
  public void prepare() {}

  /**
   * A priority for determine which actions should get processed first.
   *
   * <p>The actions will be sorted by priority and work in order. Smaller numbers have higher
   * priority. This method should return a constant.
   */
  public abstract int priority();

  /**
   * The number of minutes to wait before attempting to retry this action.
   *
   * <p>If an action has to be re-attempted, the action processor can wait until a certain window
   * expires before it will call {@link #perform(ActionServices)} again. This only sets a lower
   * limit on how frequently an action can be retried; there is no upper limit. This method should
   * return a constant.
   */
  public abstract long retryMinutes();

  /** Check if the action matches the regex query supplied by the user */
  public abstract boolean search(Pattern query);

  /**
   * Render the action to JSON for display by the front end.
   *
   * <p>It should set the <tt>type</tt> property.
   */
  public abstract ObjectNode toJson(ObjectMapper mapper);

  /** The action's name as it will appear in the JSON. */
  public final String type() {
    return type;
  }
}
