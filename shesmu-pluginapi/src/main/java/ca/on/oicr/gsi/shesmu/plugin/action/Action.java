package ca.on.oicr.gsi.shesmu.plugin.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

  /**
   * Called when an action is accepted into the scheduler.
   *
   * <p>Since the scheduler will deduplicate the same action many times, most actions are fated to
   * die quickly. This method will be called to indicate that this instance of the object will
   * actually be used. It is called after {@link #prepare()} and before {@link
   * #perform(ActionServices)}.
   *
   * @param actionId the action ID recorded for this action
   */
  public void accepted(String actionId) {}

  /**
   * Get the actions that are appropriate for this action
   *
   * @return a stream of commands that the user can perform on this action
   */
  public Stream<ActionCommand<?>> commands() {
    return Stream.empty();
  }

  @Override
  public abstract boolean equals(Object other);

  /**
   * Get the time this action was last modified outside of Shesmu
   *
   * <p>Since actions are associated with an external entity of some kind, get most recent
   * update/event/completion time if available.
   *
   * @return the time or null, if not available
   */
  public Optional<Instant> externalTimestamp() {
    return Optional.empty();
  }

  /**
   * Produce a reproducible unique identifier for this action
   *
   * <p>It should use the same data as {@link #equals(Object)}
   *
   * @param digest a callback to write bytes into a cryptographic hash; this may be called as many
   *     times as required
   */
  public abstract void generateUUID(Consumer<byte[]> digest);

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

  /**
   * The amount of time the {@link #perform(ActionServices)} method should be allowed to run for
   * before being interrupted
   */
  public Duration performTimeout() {
    return Duration.of(1, ChronoUnit.HOURS);
  }

  /** Perform any preparation needed after parameters have been set. */
  public void prepare() {}

  /**
   * A priority for determine which actions should get processed first.
   *
   * <p>The actions will be sorted by priority and work in order. Smaller numbers have higher
   * priority. This method should return a constant.
   */
  public abstract int priority();

  /** If an action is deleted, do any necessary state clean up */
  public void purgeCleanup() {}

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
   * Self-determined tags that should be attached to this action
   *
   * <p>An olive may attach tags to an action and an action may also self-determine tags it wants
   * associated with itself. The tags from olives accumulate, but the self-determined tags are
   * refresed every time, so an action can choose to drop self-determined tags. This method will be
   * used frequently, so it must be fast.
   *
   * @return A stream of tags
   */
  public Stream<String> tags() {
    return Stream.empty();
  }

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
