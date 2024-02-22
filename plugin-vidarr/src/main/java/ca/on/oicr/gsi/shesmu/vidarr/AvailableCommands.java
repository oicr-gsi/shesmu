package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import java.util.Optional;
import java.util.stream.Stream;

public enum AvailableCommands {
  RESET_ONLY(false) {
    @Override
    public Stream<ActionCommand<?>> commands() {
      return Stream.of(RESET);
    }
  },
  CAN_RETRY(true) {
    @Override
    public Stream<ActionCommand<?>> commands() {
      return Stream.of(DELETE, RETRY_PROVISION_OUT, RESET);
    }
  },
  CAN_REATTEMPT(true) {
    @Override
    public Stream<ActionCommand<?>> commands() {
      return Stream.of(DELETE, REATTEMPT, RESET);
    }
  };
  static final ActionCommand<SubmitAction> DELETE =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-DELETE",
          FrontEndIcon.PLUG,
          "Delete and Purge",
          Preference.ALLOW_BULK,
          Preference.PROMPT,
          Preference.ANNOY_USER) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          return action.owner.get().url().map(action.state::delete).orElse(false)
              ? Response.PURGE
              : Response.IGNORED;
        }
      };
  static final ActionCommand<SubmitAction> REATTEMPT =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-REATTEMPT",
          FrontEndIcon.ARROW_REPEAT,
          "Reattempt Failed Workflow",
          10,
          Preference.PROMPT,
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          final var result = action.state.reattempt();
          result.ifPresent(s -> action.state = s);
          return result.isPresent() ? Response.RESET : Response.IGNORED;
        }
      };
  static final ActionCommand<SubmitAction> RESET =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-RESET",
          FrontEndIcon.PLUG,
          "Search Vidarr Again",
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          action.state = new RunStateAttemptSubmit();
          return Response.RESET;
        }
      };
  static final ActionCommand<SubmitAction> RETRY_PROVISION_OUT =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-RETRY-PROVISION-OUT",
          FrontEndIcon.ARROW_RIGHT_SQUARE_FILL,
          "Retry Provision Out",
          10,
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          return action.owner.get().url().map(action.state::retry).orElse(false)
              ? Response.ACCEPTED
              : Response.IGNORED;
        }
      };
  private final boolean canRetry;

  AvailableCommands(boolean canRetry) {
    this.canRetry = canRetry;
  }

  public boolean canRetry() {
    return canRetry;
  }

  public abstract Stream<ActionCommand<?>> commands();
}
