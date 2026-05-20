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
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
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
    final HttpResponse<Supplier<WorkflowRunStatusResponse>> response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(url).GET().build(),
            new JsonBodyHandler<>(MAPPER, WorkflowRunStatusResponse.class));
    if (response.statusCode() == 200) {
      final WorkflowRunStatusResponse result = response.body().get();

      // Translating operation statuses to action states is the same for both modes
      final ActionState status = RunStateMonitor.actionStatusForWorkflowRun(result);
      if (status == ActionState.FAILED) {
        return new PerformResult(
            List.of("Reprovisioning has failed while executing. See provisioner logs for details."),
            status,
            new ImportStateMonitor(result.getWorkflowRunUrl(), result));
      } else {
        return new PerformResult(
            List.of(), status, new ImportStateMonitor(url.toASCIIString(), result));
      }
    } else if (response.statusCode() == 404) {
      return new PerformResult(
          List.of("Imported workflow run is missing. Where did it go?"),
          ActionState.WAITING,
          new ImportStateAttemptSubmit(0));
    } else {
      return new PerformResult(
          List.of("Error getting workflow status."),
          ActionState.UNKNOWN,
          new ImportStateAttemptSubmit());
    }
  }

  @Override
  public AvailableCommands commands() {
    ActionState state = RunStateMonitor.actionStatusForWorkflowRun(status);
    if (state.equals(ActionState.FAILED)) {
      return AvailableCommands.CAN_REATTEMPT;
    }
    return AvailableCommands.RESET_ONLY;
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.of(status.getModified().toInstant());
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl, ImportRequest request, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    return create(vidarrUrl, status.getId());
  }

  @Override
  public Optional<ImportState> reattempt() {
    return commands().canRetry()
        ? Optional.of(new ImportStateAttemptSubmit(status.getAttempt() + 1))
        : Optional.empty();
  }

  @Override
  public long retryMinutes() {
    return 5;
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(status.getId()).matches();
  }

  @Override
  public OptionalInt sortKey(String key) {
    return Optional.ofNullable(status.getTracing())
        .map(m -> m.get(key))
        .map(v -> OptionalInt.of(v.intValue()))
        .orElseGet(OptionalInt::empty);
  }

  @Override
  public Stream<String> sortKeys() {
    return Optional.ofNullable(status.getTracing()).stream().flatMap(m -> m.keySet().stream());
  }

  @Override
  public Stream<String> tags() {
    return Stream.of(
        status.getCompleted() == null ? "vidarr-state:active" : "vidarr-state:finished");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("importState", "monitor");
    node.putPOJO("info", status);
    node.put("workflowRunUrl", workflowRunUrl);
  }
}
