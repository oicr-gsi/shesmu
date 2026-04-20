package ca.on.oicr.gsi.shesmu.vidarr;

import static ca.on.oicr.gsi.shesmu.vidarr.VidarrPlugin.MAPPER;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.ImportRequest;
import ca.on.oicr.gsi.vidarr.api.WorkflowRunStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportStateMonitor extends ImportState {
  private final String workflowRunUrl;
  private final WorkflowRunStatusResponse status;

  public ImportStateMonitor(String workflowRunUrl, WorkflowRunStatusResponse status) {
    this.workflowRunUrl = workflowRunUrl;
    this.status = status;
  }

  public static PerformResult create(URI vidarrUrl, String id)
      throws IOException, InterruptedException {
    final URI url = vidarrUrl.resolve("/api/status/" + id);
    final var response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(url).GET().build(),
            new JsonBodyHandler<>(MAPPER, WorkflowRunStatusResponse.class));
    if (response.statusCode() == 200) {
      final WorkflowRunStatusResponse result = response.body().get();

      // Translating operation statuses to action states is the same for both modes
      final ActionState status = RunStateMonitor.actionStatusForWorkflowRun(result);
      return new PerformResult(
          status == ActionState.FAILED
              ? List.of(
                  "Reprovisioning has failed while executing. See provisioner logs for details.")
              : List.of(),
          status,
          new ImportStateMonitor(url.toASCIIString(), result));
    } else {
      return new PerformResult(
          List.of("Error getting workflow status."),
          ActionState.UNKNOWN,
          new ImportStateAttemptSubmit());
    }
  }

  @Override
  public AvailableCommands commands() {
    return AvailableCommands.RESET_ONLY;
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl, ImportRequest request, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    return create(vidarrUrl, status.getId());
  }

  @Override
  public Optional<ImportState> reattempt() {
    return Optional.empty();
  }

  @Override
  public boolean retry(URI vidarrUrl) {
    return false;
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
  public OptionalInt sortKey(String key) {
    return OptionalInt.empty();
  }

  @Override
  public Stream<String> sortKeys() {
    return Stream.empty();
  }

  @Override
  public Stream<String> tags() {
    return Stream.empty();
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("importState", "monitor");
    node.putPOJO("info", status);
    node.put("workflowRunUrl", workflowRunUrl);
  }
}
