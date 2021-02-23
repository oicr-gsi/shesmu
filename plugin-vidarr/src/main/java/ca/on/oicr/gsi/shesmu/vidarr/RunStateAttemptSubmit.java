package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponse;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponseConflict;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponseDryRun;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponseFailure;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponseMissingKeyVersions;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowResponseSuccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** State when we have no knowledge of what's going on in Vidarr */
final class RunStateAttemptSubmit extends RunState {
  private final int attempt;
  private int retryMinutes = 5;

  public RunStateAttemptSubmit() {
    this(0);
  }

  public RunStateAttemptSubmit(int attempt) {
    this.attempt = attempt;
  }

  @Override
  public boolean canReattempt() {
    return false;
  }

  @Override
  public boolean delete(URI vidarrUrl) {
    return false;
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.empty();
  }

  @Override
  public PerformResult perform(URI vidarrUrl, SubmitWorkflowRequest request)
      throws IOException, InterruptedException {
    request.setAttempt(attempt);
    final var response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(vidarrUrl.resolve("/api/submit"))
                .POST(BodyPublishers.ofByteArray(VidarrPlugin.MAPPER.writeValueAsBytes(request)))
                .build(),
            new JsonBodyHandler<>(VidarrPlugin.MAPPER, SubmitWorkflowResponse.class));
    switch (response.statusCode()) {
      case 200:
        {
          final var result = response.body().get();
          if (result instanceof SubmitWorkflowResponseSuccess) {
            return RunStateMonitor.create(
                vidarrUrl, ((SubmitWorkflowResponseSuccess) result).getId());
          } else {
            return new PerformResult(
                List.of("Server said success but returned a failure response."),
                ActionState.UNKNOWN,
                this);
          }
        }
      case 400:
      case 409:
        {
          retryMinutes = Math.min(retryMinutes * 2, 60);
          final var result = response.body().get();
          if (result instanceof SubmitWorkflowResponseFailure) {
            return new PerformResult(
                ((SubmitWorkflowResponseFailure) result).getErrors(), ActionState.FAILED, this);
          } else if (result instanceof SubmitWorkflowResponseMissingKeyVersions) {
            final var keyResult = ((SubmitWorkflowResponseMissingKeyVersions) result);
            final var nextState = new RunStateMissing(keyResult.getId(), keyResult.getKeys());
            return new PerformResult(nextState.errors(), ActionState.FAILED, this);
          } else if (result instanceof SubmitWorkflowResponseConflict) {
            final var conflict = ((SubmitWorkflowResponseConflict) result);
            final var nextState = new RunStateConflicted(conflict.getIds());
            return new PerformResult(nextState.errors(), ActionState.HALP, nextState);
          } else if (result instanceof SubmitWorkflowResponseDryRun) {
            return new PerformResult(List.of(), ActionState.ZOMBIE, new RunStateDead());
          } else {
            return new PerformResult(
                List.of("Server said failure but returned an unexpected response."),
                ActionState.UNKNOWN,
                this);
          }
        }
      default:
        return new PerformResult(
            List.of(
                String.format("Unexpected HTTP response %d from server.", response.statusCode())),
            ActionState.UNKNOWN,
            this);
    }
  }

  @Override
  public Optional<RunState> reattempt() {
    return Optional.empty();
  }

  @Override
  public long retryMinutes() {
    return retryMinutes;
  }

  @Override
  public Stream<String> tags() {
    return Stream.of("vidarr-attempt:" + attempt);
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("state", "attempt");
    node.put("attempt", attempt);
  }
}
