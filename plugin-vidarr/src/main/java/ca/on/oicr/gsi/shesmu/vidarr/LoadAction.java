package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Load a workflow run into Vidarr using injection mode (Vidarr computes the hash IDs) without
 * reprovisioning any of its files.
 *
 * <p>Vidarr does not return the ID of the loaded workflow run, so there is nothing to monitor; once
 * Vidarr accepts the load, the action is complete. Loading is idempotent in Vidarr, so resubmitting
 * an already-loaded workflow run is harmless.
 */
public class LoadAction extends BaseLoadAction {
  boolean loaded;
  private Instant loadedTime;
  // Vidarr refused the request (400); resending it unchanged won't help, so wait for a reset
  private boolean rejected;
  private int retryMinutes = 5;

  public LoadAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-load", owner, workflow);
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return AvailableCommands.RESET_ONLY.commands();
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.ofNullable(loadedTime);
  }

  @Override
  protected Optional<ActionState> settledState() {
    if (loaded) {
      return Optional.of(ActionState.SUCCEEDED);
    }
    if (rejected) {
      return Optional.of(ActionState.HALP);
    }
    return Optional.empty();
  }

  @Override
  protected ActionState performWithVidarr(
      URI vidarrUrl, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    final HttpResponse<String> response =
        VidarrPlugin.CLIENT.send(
            HttpRequest.newBuilder(vidarrUrl.resolve("/api/load-injected"))
                .header("Content-type", "application/json")
                .version(HttpClient.Version.HTTP_1_1)
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        VidarrPlugin.MAPPER.writeValueAsBytes(request.load())))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    switch (response.statusCode()) {
      case 200:
        loaded = true;
        loadedTime = Instant.now();
        retryMinutes = 5;
        errors = List.of();
        return ActionState.SUCCEEDED;
      case 400: // Malformed or invalid request
        errors = List.of(response.body());
        rejected = true;
        return ActionState.HALP;
      case 409: // Conflicts with an existing workflow run
      case 500: // Internal Server Error
        retryMinutes = Math.min(retryMinutes * 2, 60);
        errors = List.of(response.body());
        return ActionState.FAILED;
      case 507: // Too many load/unload requests queued in Vidarr
        errors = List.of(response.body());
        return ActionState.THROTTLED;
      default:
        errors =
            List.of(
                String.format("Unexpected HTTP response %d from server.", response.statusCode()));
        return ActionState.UNKNOWN;
    }
  }

  void reset() {
    loaded = false;
    loadedTime = null;
    rejected = false;
    retryMinutes = 5;
    errors = List.of();
  }

  @Override
  public long retryMinutes() {
    return retryMinutes;
  }

  @Override
  public Stream<String> tags() {
    return Stream.concat(
        super.tags(), Stream.of(loaded ? "vidarr-state:loaded" : "vidarr-state:attempt"));
  }

  @Override
  public ObjectNode toJson(JsonMapper mapper) {
    ObjectNode node = super.toJson(mapper);
    node.put("loadState", loaded ? "loaded" : "attempt");
    return node;
  }
}
