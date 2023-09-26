package ca.on.oicr.gsi.shesmu.plugin.action;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Perform an action-specific command
 *
 * <p>Commands are a way for a user to provide specific direction to actions. This is meant to
 * provide a way to adjust an action's behaviour at runtime. Actions may receive invalid or
 * inappropriate commands and should simply ignore them.
 *
 * @param <A> the action that should receive this command
 */
public abstract class ActionCommand<A extends Action> {

  /**
   * Rules for how the UI should show these actions
   *
   * <p>Note that these are not enforced and using the REST API, any actions can be applied.
   */
  public enum Preference {
    /** The UI should display a confirmation dialog */
    PROMPT,
    /** The action can be performed on a collection of actions at once */
    ALLOW_BULK,
    /** Make the user perform some annoying task before allowing them to execute the action. */
    ANNOY_USER
  }

  /** The result of updating the action */
  public abstract static class Response {
    /** The action executed this command, but no visible state changes occurred. */
    public static final Response ACCEPTED =
        new Response() {
          @Override
          public <F, R> R apply(
              ResponseVisitor<R, F> visitor,
              ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
            return visitor.accepted();
          }
        };
    /** This action was not able to execute this command. */
    public static final Response IGNORED =
        new Response() {
          @Override
          public <F, R> R apply(
              ResponseVisitor<R, F> visitor,
              ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
            return visitor.ignored();
          }
        };
    /** The action executed this command and now needs to be purged. */
    public static final Response PURGE =
        new Response() {
          @Override
          public <F, R> R apply(
              ResponseVisitor<R, F> visitor,
              ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
            return visitor.purge();
          }
        };
    /**
     * The action executed this command and needs to put back in the {@link ActionState#UNKNOWN}
     * state
     */
    public static final Response RESET =
        new Response() {
          @Override
          public <F, R> R apply(
              ResponseVisitor<R, F> visitor,
              ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
            return visitor.reset();
          }
        };

    /**
     * Create an action which purges itself and triggers other actions matching a filter to be
     * purged
     *
     * @param murderer the filter to select actions to be purged
     * @return a response state indicating that this action and others must be purged
     */
    public static Response murder(Murderer murderer) {
      return new Response() {

        @Override
        public <F, R> R apply(
            ResponseVisitor<R, F> visitor,
            ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
          return visitor.murder(murderer.murder(builder));
        }
      };
    }

    /**
     * Call a visitor based on this response state
     *
     * @param visitor the visitor to use
     * @param builder the action filter builder to use
     * @return the result from the visitor
     * @param <F> the filter type
     * @param <R> the result type
     */
    public abstract <F, R> R apply(
        ResponseVisitor<R, F> visitor,
        ActionFilterBuilder<F, ActionState, String, Instant, Long> builder);
  }

  /** Create an action filter for purging actions */
  public interface Murderer {

    /**
     * Create a new filter to determine which actions should be purged
     *
     * @param builder the filter builder to use
     * @return the constructed filter
     * @param <F> the filter type
     */
    <F> F murder(ActionFilterBuilder<F, ActionState, String, Instant, Long> builder);
  }

  /**
   * Visit an action command response
   *
   * @param <R> the return type from the visitor
   * @param <F> the action filter output type
   */
  public interface ResponseVisitor<R, F> {

    /**
     * Called when the action indicated it accepted the command
     *
     * @return implementation specific result information
     */
    R accepted();

    /**
     * Called when the action indicated it ignored the command
     *
     * @return implementation specific result information
     */
    R ignored();

    /**
     * Called when the action indicated it accepted the command and wants to be purged
     *
     * @return implementation specific result information
     */
    R purge();

    /**
     * Called when the action indicated it accepted the command and wants to be put in an unknown
     * action state
     *
     * @return implementation specific result information
     */
    R reset();

    /**
     * Called when the action indicated it accepted the command and wants to be purged along with
     * any actions matching the supplied filter
     *
     * @param filter the action filter to use
     * @return implementation specific result information
     */
    R murder(F filter);
  }

  private final String buttonText;
  private final Class<A> clazz;
  private final String command;
  private final FrontEndIcon icon;
  private final EnumSet<Preference> preferences = EnumSet.noneOf(Preference.class);

  /**
   * Define a new command
   *
   * @param clazz the action class on which this command is performed
   * @param command an identifier for this command; although the implementation is unique to this
   *     action type, this can be shared across different action types for bulk actions
   * @param icon the icon that should appear in the UI
   * @param buttonText the text that should appear in the UI
   * @param preferences UI settings for this command
   */
  public ActionCommand(
      Class<A> clazz,
      String command,
      FrontEndIcon icon,
      String buttonText,
      Preference... preferences) {
    this.clazz = clazz;
    this.command = command;
    this.icon = icon;
    this.buttonText = buttonText;
    this.preferences.addAll(List.of(preferences));
  }

  /**
   * The text that the user sees
   *
   * @return the display text
   */
  public final String buttonText() {
    return buttonText;
  }

  /**
   * The command identifier
   *
   * @return a unique identifier for this action
   */
  public final String command() {
    return command;
  }

  /**
   * Execute a command on an action
   *
   * @param action the action to perform the command on
   * @param user the user performing this action, if known
   * @return an indication of whether the command was followed and if the processor needs to change
   *     the action's state
   */
  protected abstract Response execute(A action, Optional<String> user);

  /**
   * The front end icon
   *
   * @return the icon identifier
   */
  public FrontEndIcon icon() {
    return icon;
  }

  /**
   * Check if a UI preference is set
   *
   * @param preference the UI preference to interrogate
   * @return true if the preference should be set
   */
  public final boolean prefers(Preference preference) {
    return preferences.contains(preference);
  }

  /**
   * Attempt to execute a command on an action
   *
   * @param action the action to perform the command on
   * @param command the action identifier
   * @param user the user performing this action, if known
   * @return an indication of whether the command was follow and if the processor needs to change
   *     the action's state
   */
  public final Response process(Action action, String command, Optional<String> user) {
    if (command.equals(this.command) && clazz.isInstance(action)) {
      return execute(clazz.cast(action), user);
    }
    return Response.IGNORED;
  }
}
