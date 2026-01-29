package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.ImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportAction extends Action {
  private boolean stale;
  private int priority;
  final ImportRequest request = new ImportRequest();
  final Supplier<VidarrPlugin> owner;
  private final Set<String> services = new TreeSet<>(List.of("vidarr"));
  ImportState state = new ImportStateAttemptSubmit();
  List<String> errors = List.of();

  // private final List<String> tags;

  public ImportAction(Supplier<VidarrPlugin> owner) {
    super("vidarr-import");
    this.owner = owner;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (null == other || getClass() != other.getClass()) return false;
    ImportAction o = (ImportAction) other;
    return stale == o.stale && Objects.equals(this.request, o.request);
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return state.commands().commands();
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {}

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    if (stale) {
      return ActionState.ZOMBIE;
    }
    final Set<String> throttled = services.isOverloaded(this.services);
    if (!throttled.isEmpty()) {
      errors = List.of("Services are unavailable: ", String.join(", ", throttled));
      return ActionState.THROTTLED;
    }
    final ImportState.PerformResult result =
        owner
            .get()
            .url()
            .map(
                url -> {
                  try {
                    return state.perform(url, request, lastGeneratedByOlive, isOliveLive);
                  } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    return new ImportState.PerformResult(
                        List.of(e.getMessage()), ActionState.UNKNOWN, state);
                  }
                })
            .orElseGet(
                () ->
                    new ImportState.PerformResult(
                        List.of("Internal error: No Vidarr URL available"),
                        ActionState.UNKNOWN,
                        state));
    errors = result.errors();
    state = result.nextState();
    return result.actionState();
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 0;
  }

  @Override
  public boolean search(Pattern query) {
    return false;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    return null;
  }
}
