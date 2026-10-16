package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class ImportAction extends BaseLoadAction {
  ImportState state = new ImportStateAttemptSubmit();

  public ImportAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-import", owner, workflow);
  }

  @ActionParameter(name = "output_provisioner")
  public void outputProvisioner(String name) {
    request.setOutputProvisionerName(name);
  }

  @ActionParameter(name = "new_output_dir")
  public void newOutputDir(String path) {
    request.setOutputPath(path);
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return state.commands().commands();
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return state.externalTimestamp();
  }

  @Override
  protected ActionState performWithVidarr(
      URI vidarrUrl, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    final ImportState.PerformResult result =
        state.perform(vidarrUrl, request, lastGeneratedByOlive, isOliveLive);
    errors = result.errors();
    state = result.nextState();
    return result.actionState();
  }

  @Override
  public long retryMinutes() {
    return state.retryMinutes();
  }

  @Override
  protected boolean searchState(Pattern query) {
    return state.search(query);
  }

  @Override
  public OptionalInt sortKey(String key) {
    if (key.equals(SORT_KEY_ATTEMPT)) {
      if (state instanceof ImportStateAttemptSubmit) {
        return OptionalInt.of(((ImportStateAttemptSubmit) state).getAttempt());
      } else {
        return OptionalInt.of(request.getAttempt());
      }
    } else {
      return state.sortKey(key);
    }
  }

  @Override
  public Stream<String> tags() {
    return Stream.concat(super.tags(), state.tags());
  }

  @Override
  public ObjectNode toJson(JsonMapper mapper) {
    ObjectNode node = super.toJson(mapper);
    state.writeJson(mapper, node);
    return node;
  }
}
