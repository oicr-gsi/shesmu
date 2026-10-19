package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Load a workflow run into Vidarr using injection mode (Vidarr computes the hash IDs)
 *
 * <p>Vidarr does not return the ID of the loaded workflow run, so there is nothing to monitor; once
 * Vidarr accepts the load, the action is complete. Loading is idempotent in Vidarr, so resubmitting
 * an already-loaded workflow run is harmless.
 *
 * <p>The load endpoint takes an {@link UnloadedData} rather than an {@link ImportRequest}, so the
 * request is converted with {@link ImportRequest#load()} when it is sent. The import-only fields
 * (attempt, output provisioner, and output path) are never set or used.
 */
public class LoadAction extends BaseLoadAction {
  boolean loaded;
  private int retryMinutes = 5;

  public LoadAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-load", owner, workflow);
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return AvailableCommands.RESET_ONLY.commands();
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
        errors = List.of();
        return ActionState.SUCCEEDED;
      case 400: // Malformed or invalid request
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
