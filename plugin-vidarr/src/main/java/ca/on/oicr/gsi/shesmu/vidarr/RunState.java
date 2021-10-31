package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * As Shesmu communicates with Vidarr, the action needs to track whats going
 * on. This class is designed as a state machine where each implementation can
 * determine what to do and returns a next state.
 */
abstract class RunState {

  public static final class PerformResult {
    private final ActionState actionState;
    private final List<String> errors;
    private final RunState nextState;

    public PerformResult(List<String> errors, ActionState actionState, RunState nextState) {
      this.errors = errors;
      this.actionState = actionState;
      this.nextState = nextState;
    }

    public ActionState actionState() {
      return actionState;
    }

    public List<String> errors() {
      return errors;
    }

    public RunState nextState() {
      return nextState;
    }
  }

  public abstract boolean canReattempt();

  public abstract boolean delete(URI vidarrUrl);

  public abstract Optional<Instant> externalTimestamp();

  public abstract PerformResult perform(URI vidarrUrl, SubmitWorkflowRequest request)
      throws IOException, InterruptedException;

  public abstract Optional<RunState> reattempt();

  public abstract long retryMinutes();

  public abstract Stream<String> tags();

  public abstract void writeJson(ObjectMapper mapper, ObjectNode node);
}
