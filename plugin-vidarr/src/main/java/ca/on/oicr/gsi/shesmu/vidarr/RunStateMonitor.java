package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import ca.on.oicr.gsi.vidarr.api.WorkflowRunStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class RunStateMonitor extends RunState {

  private static ActionState actionStatusForWorkflowRun(WorkflowRunStatusResponse response) {
    // It happens in some rare (and typically, bugged) cases for all operations to succeed but the
    // engine itself to fail.
    // This state leaves broken actions stuck in QUEUED. Check early for engine failure to ensure
    // correct ActionState.
    // If the workflow run is completed, then enginephase will be null, as enginephase belongs to
    // the
    // active_workflow_run record associated with the workflow_run, and the active_workflow_run
    // record is removed
    // when a workflow run is successful.
    if (null != response.getEnginePhase() && response.getEnginePhase().equals("FAILED")) {
      return ActionState.FAILED;
    }
    if (response.getCompleted() != null) {
      return ActionState.SUCCEEDED;
    }
    switch (response.getOperationStatus()) {
      case "SUCCEEDED":
        // If a workflow has only succeeded operations, but isn't finished itself, it must be
        // waiting to start the next batch of operations.
        return ActionState.QUEUED;
      case "FAILED":
        return ActionState.FAILED;
      case "WAITING":
        return ActionState.INFLIGHT;
      case "N/A":
        return ActionState.WAITING;
      default:
        return ActionState.UNKNOWN;
    }
  }

  public static PerformResult create(URI vidarrUrl, String id)
      throws IOException, InterruptedException {
    final var workflowRunUrl = vidarrUrl.resolve("/api/status/" + id);
    final var response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(workflowRunUrl).GET().build(),
            new JsonBodyHandler<>(VidarrPlugin.MAPPER, WorkflowRunStatusResponse.class));
    if (response.statusCode() == 200) {
      final var result = response.body().get();
      final var status = actionStatusForWorkflowRun(result);
      return new PerformResult(
          status == ActionState.FAILED
              ? List.of(
                  "Workflow run has failed while executing. See workflow run logs for details.")
              : List.of(),
          status,
          new RunStateMonitor(workflowRunUrl.toASCIIString(), result));
    } else if (response.statusCode() == 404) {
      return new PerformResult(
          List.of("Workflow run was deleted."), ActionState.WAITING, new RunStateAttemptSubmit(0));
    } else {
      return new PerformResult(
          List.of("Error getting workflow status."),
          ActionState.UNKNOWN,
          new RunStateAttemptSubmit(0));
    }
  }

  private final WorkflowRunStatusResponse status;
  private final String workflowRunUrl;

  public RunStateMonitor(String workflowRunUrl, WorkflowRunStatusResponse status) {
    super();
    this.workflowRunUrl = workflowRunUrl;
    this.status = status;
  }

  @Override
  public boolean canReattempt() {
    // Failed operations may reattempt
    return status.getOperationStatus().equals("FAILED")
        // as may engine failures
        || (status.getEnginePhase() != null
            && (status.getEnginePhase().equals("WAITING_FOR_RESOURCES")
                || status.getEnginePhase().equals("FAILED")))
        // Succeeded actions may not but otherwise-empty engine status probably
        || (status.getEnginePhase() == null && !status.getOperationStatus().equals("N/A"));
  }

  @Override
  public boolean delete(URI vidarrUrl) {
    if (canReattempt()) {
      try {
        final var response =
            VidarrPlugin.CLIENT.send(
                HttpRequest.newBuilder(vidarrUrl.resolve("/api/status/" + status.getId()))
                    .DELETE()
                    .build(),
                BodyHandlers.discarding());
        return response.statusCode() == 200;
      } catch (InterruptedException | IOException e) {
        e.printStackTrace();
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.of(status.getModified().toInstant());
  }

  @Override
  public PerformResult perform(URI vidarrUrl, SubmitWorkflowRequest request)
      throws IOException, InterruptedException {
    return create(vidarrUrl, status.getId());
  }

  @Override
  public Optional<RunState> reattempt() {
    return canReattempt()
        ? Optional.of(new RunStateAttemptSubmit(status.getAttempt() + 1))
        : Optional.empty();
  }

  @Override
  public long retryMinutes() {
    return 5;
  }

  @Override
  public Stream<String> tags() {
    return Stream.of(
        "vidarr-workflow-run:" + status.getId(),
        status.getCompleted() == null ? "vidarr-state:active" : "vidarr-state:finished");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("runState", "monitor");
    node.putPOJO("info", status);
    node.put("workflowRunUrl", workflowRunUrl);
  }
}
