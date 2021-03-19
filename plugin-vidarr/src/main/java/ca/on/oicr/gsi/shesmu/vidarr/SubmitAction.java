package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.vidarr.api.ExternalKey;
import ca.on.oicr.gsi.vidarr.api.SubmitMode;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SubmitAction extends Action {
  private static final ActionCommand<SubmitAction> DELETE =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-DELETE",
          FrontEndIcon.PLUG,
          "Delete and Purge",
          Preference.ALLOW_BULK,
          Preference.PROMPT) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          return action.owner.get().url().map(action.state::delete).orElse(false)
              ? Response.PURGE
              : Response.IGNORED;
        }
      };
  static final Imyhat EXTERNAL_IDS =
      new ObjectImyhat(
              Stream.of(new Pair<>("id", Imyhat.STRING), new Pair<>("provider", Imyhat.STRING)))
          .asList();
  private static final ActionCommand<SubmitAction> REATTEMPT =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-REATTEMPT",
          FrontEndIcon.ARROW_REPEAT,
          "Reattempt Failed Workflow",
          Preference.PROMPT,
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          final var result = action.state.reattempt();
          result.ifPresent(s -> action.state = s);
          return result.isPresent() ? Response.RESET : Response.IGNORED;
        }
      };
  private static final ActionCommand<SubmitAction> RESET =
      new ActionCommand<>(
          SubmitAction.class,
          "VIDARR-RESET",
          FrontEndIcon.PLUG,
          "Search Vidarr Again",
          Preference.ALLOW_BULK) {
        @Override
        protected Response execute(SubmitAction action, Optional<String> user) {
          action.state = new RunStateAttemptSubmit();
          return Response.RESET;
        }
      };

  private static boolean checkJson(JsonNode json, Pattern query) {
    switch (json.getNodeType()) {
      case ARRAY:
        {
          for (final var element : json) {
            if (checkJson(element, query)) {
              return true;
            }
          }
          return false;
        }
      case BOOLEAN:
        return query.matcher(Boolean.toString(json.asBoolean())).matches();
      case NUMBER:
        return query.matcher(json.numberValue().toString()).matches();
      case OBJECT:
        {
          final var iterator = json.fields();
          while (iterator.hasNext()) {
            final var field = iterator.next();
            if (query.matcher(field.getKey()).matches() || checkJson(field.getValue(), query)) {
              return true;
            }
          }
          return false;
        }
      case STRING:
        return query.matcher(json.asText()).matches();
      default:
        return false;
    }
  }

  private List<String> errors = List.of();
  private final Supplier<VidarrPlugin> owner;
  private int priority;
  final SubmitWorkflowRequest request = new SubmitWorkflowRequest();
  private final Set<String> services = new TreeSet<>(List.of("vidarr"));
  private boolean stale;
  private RunState state = new RunStateAttemptSubmit();
  private final List<String> tags;

  public SubmitAction(
      Supplier<VidarrPlugin> owner,
      String targetName,
      String workflowName,
      String workflowVersion) {
    super("vidarr-run");
    this.owner = owner;
    request.setConsumableResources(new TreeMap<>());
    request.setTarget(targetName);
    request.setWorkflow(workflowName);
    request.setWorkflowVersion(workflowVersion);
    request.setLabels(VidarrPlugin.MAPPER.createObjectNode());
    tags =
        List.of(
            "vidarr-target:" + targetName,
            "vidarr-workflow:" + workflowName,
            "vidarr-workflow:" + workflowName + "/" + workflowVersion);
    priority = workflowName.hashCode() % 100;
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return state.canReattempt() ? Stream.of(DELETE, REATTEMPT, RESET) : Stream.of(RESET);
  }

  @ActionParameter(name = "dry_run", required = false)
  public void dryRun(boolean dryRun) {
    request.setMode(dryRun ? SubmitMode.DRY_RUN : SubmitMode.RUN);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var that = (SubmitAction) o;
    return stale == that.stale && request.equalsIgnoreAttempt(that.request);
  }

  @SuppressWarnings("unchecked")
  @ActionParameter(name = "external_keys", type = "ao4id$sprovider$sstale$bversions$mss")
  public void externalKeys(Set<Tuple> values) {
    request.setExternalKeys(
        values.stream()
            .map(
                value -> {
                  final var externalKey = new ExternalKey();
                  externalKey.setId((String) value.get(0));
                  externalKey.setProvider((String) value.get(1));
                  stale |= (Boolean) value.get(2);
                  externalKey.setVersions((Map<String, String>) value.get(3));
                  return externalKey;
                })
            .collect(Collectors.toSet()));
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return state.externalTimestamp();
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(new byte[] {(byte) (stale ? 1 : 0)});
      digest.accept(VidarrPlugin.MAPPER.writeValueAsBytes(request));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    return request.hashCodeIgnoreAttempt() * 31 + Boolean.hashCode(stale);
  }

  @Override
  public synchronized ActionState perform(ActionServices services) {
    if (stale) {
      return ActionState.ZOMBIE;
    }
    final var throttled = services.isOverloaded(this.services);
    if (!throttled.isEmpty()) {
      errors = List.of("Services are unavailable: ", String.join(", ", throttled));
      return ActionState.THROTTLED;
    }
    final var result =
        owner
            .get()
            .url()
            .map(
                url -> {
                  try {
                    return state.perform(url, request);
                  } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    return new RunState.PerformResult(
                        List.of(e.getMessage()), ActionState.UNKNOWN, state);
                  }
                })
            .orElseGet(
                () ->
                    new RunState.PerformResult(
                        List.of("Internal error: No Vidarr URL available"),
                        ActionState.UNKNOWN,
                        state));
    errors = result.errors();
    state = result.nextState();
    return result.actionState();
  }

  @Override
  public int priority() {
    return priority;
  }

  @ActionParameter(required = false)
  public void priority(long priority) {
    this.priority = (int) priority;
  }

  @Override
  public long retryMinutes() {
    return state.retryMinutes();
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(request.getWorkflow()).matches()
        || query.matcher(request.getWorkflowVersion()).matches()
        || query.matcher(request.getTarget()).matches()
        || request.getExternalKeys().stream()
            .anyMatch(
                ek ->
                    query.matcher(ek.getProvider()).matches()
                        || query.matcher(ek.getId()).matches()
                        || ek.getVersions().entrySet().stream()
                            .anyMatch(
                                v ->
                                    query.matcher(v.getKey()).matches()
                                        || query.matcher(v.getValue()).matches()))
        || checkJson(request.getArguments(), query)
        || checkJson(request.getMetadata(), query)
        || checkJson(request.getEngineParameters(), query);
  }

  @ActionParameter(required = false)
  public void services(Set<String> services) {
    this.services.addAll(services);
  }

  @Override
  public Stream<String> tags() {
    return Stream.concat(tags.stream(), state.tags());
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.putPOJO("request", request);
    node.put("priority", priority);
    services.forEach(node.putArray("services")::add);
    errors.forEach(node.putArray("errors")::add);
    state.writeJson(mapper, node);
    return node;
  }
}
