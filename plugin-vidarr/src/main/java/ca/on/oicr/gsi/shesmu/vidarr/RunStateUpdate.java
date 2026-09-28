package ca.on.oicr.gsi.shesmu.vidarr;

import static java.net.http.HttpClient.*;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.*;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class RunStateUpdate extends RunState {
  private int retryMinutes = 5;
  public boolean unknown = true;

  @Override
  public boolean search(Pattern query) {
    return false;
  }

  @Override
  public AvailableCommands commands() {
    return unknown ? AvailableCommands.CAN_LAUNCH : AvailableCommands.RESET_ONLY;
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
  public OptionalInt getAttempt() {
    return OptionalInt.empty();
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl,
      SubmitWorkflowRequest request,
      SubmissionPolicy submissionPolicy,
      Duration lastGeneratedByOlive,
      boolean isOliveLive)
      throws IOException, InterruptedException {
    // TODO redundant? maybe?
    request.setMode(submissionPolicy.mode(lastGeneratedByOlive, isOliveLive));

    final HttpResponse<Supplier<SubmitWorkflowResponse>> response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(vidarrUrl.resolve("/api/submit"))
                .header("Content-type", "application/json")
                .version(Version.HTTP_1_1)
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        VidarrPlugin.MAPPER.writeValueAsBytes(request)))
                .build(),
            new JsonBodyHandler<>(VidarrPlugin.MAPPER, SubmitWorkflowResponse.class));
    switch (response.statusCode()) {
      case 200:
        {
          SubmitWorkflowResponse body = response.body().get();
          if (body instanceof SubmitWorkflowResponseSuccess) { // Keys updated
            unknown = true;
            return new PerformResult(List.of(), ActionState.SUCCEEDED, this);

          } else if (body instanceof SubmitWorkflowResponseDryRun) { // Nothing done
            return new PerformResult(
                List.of("Vidarr doesn't know this run. You can launch it though!"),
                ActionState.HALP,
                this);

          } else { // ???
            return new PerformResult(
                List.of(
                    String.format(
                        "Server claims to be happy, but got an unknown response of %s.",
                        body.getClass().getSimpleName())),
                ActionState.UNKNOWN,
                this);
          }
        }
      case 400:
      case 409:
        {
          // TODO this is the same as RunStateAttemptSubmit. Is it all needed?
          retryMinutes = Math.min(retryMinutes * 2, 60);
          final SubmitWorkflowResponse result = response.body().get();
          if (result instanceof SubmitWorkflowResponseFailure) {
            return new PerformResult(
                ((SubmitWorkflowResponseFailure) result).getErrors(), ActionState.FAILED, this);
          } else if (result instanceof SubmitWorkflowResponseMissingKeyVersions) {
            final SubmitWorkflowResponseMissingKeyVersions keyResult =
                ((SubmitWorkflowResponseMissingKeyVersions) result);
            final RunStateMissing nextState =
                new RunStateMissing(keyResult.getId(), keyResult.getKeys());
            return new PerformResult(nextState.errors(), ActionState.HALP, nextState);
          } else if (result instanceof SubmitWorkflowResponseConflict) {
            final SubmitWorkflowResponseConflict conflict =
                ((SubmitWorkflowResponseConflict) result);
            final RunStateConflicted nextState = new RunStateConflicted(conflict.getIds());
            return new PerformResult(nextState.errors(), ActionState.HALP, nextState);
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
  public boolean retry(URI vidarrUrl) {
    return false;
  }

  @Override
  public long retryMinutes() {
    return 0;
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
  public void writeJson(JsonMapper mapper, ObjectNode node) {
    node.put("runState", "update");
    node.put("unknown", unknown);
  }
}
