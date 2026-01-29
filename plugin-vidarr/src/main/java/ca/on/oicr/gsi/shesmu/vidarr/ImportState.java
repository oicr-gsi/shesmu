package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.ImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// TODO all of these can probably be refactored into RunStates with Generics or something
abstract class ImportState {
  public static final class PerformResult {
    private final ActionState actionState;
    private final List<String> errors;
    private final ImportState nextState;

    public PerformResult(List<String> errors, ActionState actionState, ImportState nextState) {
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

    public ImportState nextState() {
      return nextState;
    }
  }

  public abstract AvailableCommands commands();

  public abstract PerformResult perform(
      URI vidarrUrl, ImportRequest request, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException;

  public abstract Optional<ImportState> reattempt();

  public abstract boolean retry(URI vidarrUrl);

  public abstract long retryMinutes();

  public abstract boolean search(Pattern query);

  public abstract OptionalInt sortKey(String key);

  public abstract Stream<String> sortKeys();

  public abstract Stream<String> tags();

  public abstract void writeJson(ObjectMapper mapper, ObjectNode node);
}
