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
  static final ActionCommand<VidarrAction> REATTEMPT =
      new ActionCommand<>(
          VidarrAction.class,
          "VIDARR-REATTEMPT",
          FrontEndIcon.ARROW_REPEAT,
          "Reattempt Failed Workflow",
          10,
          Preference.PROMPT,
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(VidarrAction action, Optional<String> user) {
          return switch (action) {
            case SubmitAction submitAction:
              final Optional<RunState> submitReattempt = submitAction.state.reattempt();
              submitReattempt.ifPresent(s -> submitAction.state = s);
              yield submitReattempt.isPresent() ? Response.RESET : Response.IGNORED;
            case ImportAction importAction:
              final Optional<ImportState> importReattempt = importAction.state.reattempt();
              importReattempt.ifPresent(s -> importAction.state = s);
              yield importReattempt.isPresent() ? Response.RESET : Response.IGNORED;
            default:
              yield Response.IGNORED;
          };
        }
      };
  static final ActionCommand<VidarrAction> RESET =
      new ActionCommand<>(
          VidarrAction.class,
          "VIDARR-RESET",
          FrontEndIcon.PLUG,
          "Search Vidarr Again",
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(VidarrAction action, Optional<String> user) {
          return switch (action) {
            case SubmitAction submitAction:
              submitAction.state = new RunStateAttemptSubmit();
              yield Response.RESET;
            case ImportAction importAction:
              importAction.state = new ImportStateAttemptSubmit();
              yield Response.RESET;
            default:
              yield Response.RESET;
          };
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
