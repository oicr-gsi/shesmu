package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.JsonBodyHandler;
import ca.on.oicr.gsi.vidarr.UnloadFilter;
import ca.on.oicr.gsi.vidarr.api.UnloadRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class BaseUnloadAction extends Action {
  private static final ActionCommand<BaseUnloadAction> HUMAN_APPROVE_COMMAND =
      new ActionCommand<>(
          BaseUnloadAction.class,
          "VIDARR-HUMAN-APPROVE-UNLOAD",
          FrontEndIcon.HAND_THUMBS_UP,
          "Allow Unload",
          Preference.ALLOW_BULK,
          Preference.PROMPT,
          Preference.ANNOY_USER) {
        @Override
        protected Response execute(BaseUnloadAction action, Optional<String> user) {
          if (!action.allowedToRun) {
            action.allowedToRun = true;
            return Response.ACCEPTED;
          }
          return Response.IGNORED;
        }
      };
  private boolean allowedToRun;
  private List<String> errors = List.of();
  private final Supplier<VidarrPlugin> owner;
  private String vidarrOutputName;

  public BaseUnloadAction(String type, Supplier<VidarrPlugin> owner) {
    super("vidarr-unload-" + type);
    this.owner = owner;
  }

  protected abstract void addFilterForJson(ObjectNode node);

  @Override
  public final Stream<ActionCommand<?>> commands() {
    return allowedToRun ? Stream.empty() : Stream.of(HUMAN_APPROVE_COMMAND);
  }

  protected abstract UnloadFilter createFilter();

  @Override
  public final ActionState perform(ActionServices services) {
    if (!allowedToRun) {
      errors = List.of("Waiting for human approval before removing data from Víðarr.");
      return ActionState.HALP;
    }
    final var result =
        owner
            .get()
            .url()
            .<Pair<ActionState, List<String>>>map(
                vidarrUrl -> {
                  final var input = createFilter();
                  if (input == null) {
                    return new Pair<>(
                        ActionState.ZOMBIE,
                        List.of("No input was provided. Not going to do anything."));
                  }
                  try {
                    final var request = new UnloadRequest();
                    request.setRecursive(true);
                    request.setFilter(input);
                    final var response =
                        VidarrPlugin.CLIENT.send(
                            HttpRequest.newBuilder(vidarrUrl.resolve("/api/unload"))
                                .header("Content-type", "application/json")
                                .POST(
                                    BodyPublishers.ofByteArray(
                                        VidarrPlugin.MAPPER.writeValueAsBytes(request)))
                                .build(),
                            new JsonBodyHandler<>(VidarrPlugin.MAPPER, String.class));
                    if (response.statusCode() < 400) {
                      vidarrOutputName = response.body().get();
                      return new Pair<>(ActionState.SUCCEEDED, List.of());
                    }
                    return new Pair<>(
                        ActionState.FAILED,
                        List.of("Error from Vidarr server: " + response.statusCode()));
                  } catch (Exception e) {
                    e.printStackTrace();
                    return new Pair<>(ActionState.FAILED, List.of(e.getMessage()));
                  }
                })
            .orElse(new Pair<>(ActionState.UNKNOWN, List.of()));
    errors = result.second();
    return result.first();
  }

  @Override
  public final int priority() {
    return 0;
  }

  @Override
  public final long retryMinutes() {
    return 15;
  }

  @Override
  public final ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("vidarrOutputName", vidarrOutputName);
    errors.forEach(node.putArray("errors")::add);
    addFilterForJson(node);
    return node;
  }
}
