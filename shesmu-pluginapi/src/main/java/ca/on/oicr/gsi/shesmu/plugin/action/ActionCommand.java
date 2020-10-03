package ca.on.oicr.gsi.shesmu.plugin.action;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import java.util.Arrays;
import java.util.EnumSet;
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
    this.preferences.addAll(Arrays.asList(preferences));
  }

  /** The text that the user sees */
  public final String buttonText() {
    return buttonText;
  }

  /** The command identifier */
  public final String command() {
    return command;
  }

  /**
   * Execute a command on an action
   *
   * @param action the action to perform the command on
   * @param user the user performing this action, if known
   * @return true if the command was followed; false if inappropriate or not understood
   */
  protected abstract boolean execute(A action, Optional<String> user);

  /** The front end icon */
  public FrontEndIcon icon() {
    return icon;
  }

  /** Check if a UI preference is set */
  public final boolean prefers(Preference preference) {
    return preferences.contains(preference);
  }

  /**
   * Attempt to execute a command on an action
   *
   * @param action the action to perform the command on
   * @param command the action identifier
   * @param user the user performing this action, if known
   * @return true if the command was followed; false if inappropriate or not understood
   */
  public final boolean process(Action action, String command, Optional<String> user) {
    if (command.equals(this.command) && clazz.isInstance(action)) {
      return execute(clazz.cast(action), user);
    }
    return false;
  }
}
