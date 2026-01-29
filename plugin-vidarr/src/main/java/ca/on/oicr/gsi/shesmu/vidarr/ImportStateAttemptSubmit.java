package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportStateAttemptSubmit extends ImportState {
  private int retryMinutes = 5;
  String error = null;

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
            new JsonBodyHandler<>(VidarrPlugin.MAPPER, Object.class));
    switch (response.statusCode()) {
      case 200:
        { // Submit successful
          return ImportStateMonitor.create(vidarrUrl, ((String[]) response.body().get())[0]);
        }
      case 400:
        { // Malformed or conflict
          error = (String) response.body().get();
        }
      case 409: // ID matches multiple runs
      case 500:
        { // Internal Server Error
          retryMinutes = Math.min(retryMinutes * 2, 60);
          ImportStateDead nextState = new ImportStateDead();
          return new PerformResult(List.of(error), ActionState.FAILED, nextState);
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
  }
}
