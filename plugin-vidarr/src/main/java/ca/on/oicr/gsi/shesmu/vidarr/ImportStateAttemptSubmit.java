package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportStateAttemptSubmit extends ImportState {
  private int retryMinutes = 5;
  List<String> errors = null;

  @Override
  public AvailableCommands commands() {
    return AvailableCommands.RESET_ONLY;
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl, ImportRequest request, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    final var response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(vidarrUrl.resolve("/api/import"))
                .header("Content-type", "application/json")
                .version(HttpClient.Version.HTTP_1_1)
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        VidarrPlugin.MAPPER.writeValueAsBytes(request)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    switch (response.statusCode()) {
      case 200:
        { // Submit successful
          SubmitWorkflowResponseSuccess success =
              VidarrPlugin.MAPPER.readValue(response.body(), SubmitWorkflowResponseSuccess.class);
          return ImportStateMonitor.create(vidarrUrl, success.getId());
        }
      case 400:
        { // Malformed or conflict
          String body = response.body();
          try {
            SubmitWorkflowResponseFailure failure =
                VidarrPlugin.MAPPER.readValue(body, SubmitWorkflowResponseFailure.class);
            errors = failure.getErrors();
          } catch (Exception e) {
            errors = List.of(body, e.getMessage());
          }
        }
      case 409: // ID matches multiple runs
      case 500:
        { // Internal Server Error
          retryMinutes = Math.min(retryMinutes * 2, 60);
          ImportStateDead nextState = new ImportStateDead();
          return new PerformResult(errors, ActionState.FAILED, nextState);
        }
      case 507:
        { // Reprovisioning already underway
          // FIXME - but how? We don't know the workflow run id do we?
          throw new IllegalStateException("Alexis! Fix this!");
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
  public Optional<ImportState> reattempt() {
    return Optional.empty();
  }

  @Override
  public boolean retry(URI vidarrUrl) {
    return false;
  }

  @Override
  public long retryMinutes() {
    return retryMinutes;
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
    return Stream.of("vidarr-state:attempt");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("importState", "attempt");

    // For now, import cannot be reattempted. Put a 0 here just to keep
    // the javascript renderer happy.
    node.put("attempt", 0);
  }
}
