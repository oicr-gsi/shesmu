package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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
public class LoadAction extends VidarrAction {
  ImportRequest request;
  boolean loaded;
  private Instant loadedTime;
  // Vidarr refused the request (400/409); resending it unchanged won't help, so wait for a reset
  private boolean rejected;
  private int retryMinutes = 5;

  public LoadAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-load", owner);

    this.request = ImportRequest.fromDeclaration(workflow);

    /*
    We don't have the full text of the workflow when creating a load from information available in an olive, and
    when injection mode loading, we don't need it. The string representation of the workflow is set to an empty string
     for the sake of the injection loading on vidarr's side. Otherwise there's a null pointer exception
     */
    request.getWorkflowVersion().setWorkflow("");
    ProvenanceWorkflowRun<ExternalMultiVersionKey> workflowRun = request.getWorkflowRun();
    workflowRun.setWorkflowName(request.getWorkflow().getName());
    workflowRun.setWorkflowVersion(request.getWorkflowVersion().getVersion());
    workflowRun.setEngineParameters(VidarrPlugin.MAPPER.createObjectNode());
    workflowRun.setLabels(VidarrPlugin.MAPPER.createObjectNode());

    priority = workflow.getName().hashCode() % 100;

    tags =
        List.of(
            "vidarr-workflow:" + request.getWorkflow().getName(),
            "vidarr-workflow:"
                + request.getWorkflow().getName()
                + "/"
                + request.getWorkflowVersion().getVersion());
  }

  @ActionParameter
  public void created(Instant created) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(created, ZoneId.of("UTC"));
    request.getWorkflowRun().setCreated(zdt);
  }

  @ActionParameter
  public void completed(Instant completed) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(completed, ZoneId.of("UTC"));
    request.getWorkflowRun().setCompleted(zdt);
  }

  @ActionParameter
  public void started(Instant started) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(started, ZoneId.of("UTC"));
    request.getWorkflowRun().setStarted(zdt);
  }

  @SuppressWarnings("unchecked")
  @ActionParameter(
      type =
          "ao9checksum$schecksumType$screated$dexternalKeys$ao2id$sprovider$slabels$mssmetatype$smodified$dpath$psize$i")
  public void analysis(Set<Tuple> analysis) {
    List<ProvenanceAnalysisRecord<ExternalId>> list = new LinkedList<>();
    for (Tuple a : analysis) {
      ProvenanceAnalysisRecord<ExternalId> record = new ProvenanceAnalysisRecord<>();
      record.setChecksum((String) a.get(0));
      record.setChecksumType((String) a.get(1));
      record.setCreated(ZonedDateTime.ofInstant((Instant) a.get(2), ZoneId.of("UTC")));

      TreeSet<Tuple> ids = (TreeSet<Tuple>) a.get(3);
      List<ExternalId> keys =
          ids.stream().map(t -> new ExternalId(t.get(1).toString(), t.get(0).toString())).toList();

      record.setExternalKeys(keys);
      record.setLabels((Map<String, String>) a.get(4));
      record.setMetatype((String) a.get(5));
      record.setModified(ZonedDateTime.ofInstant((Instant) a.get(6), ZoneId.of("UTC")));
      record.setPath(((Path) a.get(7)).toString());
      record.setSize((long) a.get(8));
      record.setType("file");
      list.add(record);
    }
    request.getWorkflowRun().setAnalysis(list);
  }

  @SuppressWarnings("unchecked")
  @ActionParameter(name = "external_keys", type = "ao4id$sprovider$sstale$bversions$mss")
  public void externalKeys(Set<Tuple> values) {
    request
        .getWorkflowRun()
        .setExternalKeys(
            values.stream()
                .map(
                    value -> {
                      final ExternalMultiVersionKey externalKey = new ExternalMultiVersionKey();
                      externalKey.setId((String) value.get(0));
                      externalKey.setProvider((String) value.get(1));
                      stale |= (Boolean) value.get(2);

                      Map<String, Set<String>> versions = new HashMap<>();
                      for (Map.Entry<String, String> entry :
                          ((Map<String, String>) value.get(3)).entrySet()) {
                        versions.put(entry.getKey(), Set.of(entry.getValue()));
                      }
                      externalKey.setVersions(versions);
                      return externalKey;
                    })
                .toList());
  }

  @ActionParameter(name = "input_files")
  public void inputFiles(Set<String> files) {
    request.getWorkflowRun().setInputFiles(files.stream().toList());
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (null == other || getClass() != other.getClass()) return false;
    LoadAction o = (LoadAction) other;
    return stale == o.stale && this.request.equalsIgnoreAttempt(o.request);
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
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(new byte[] {(byte) (stale ? 1 : 0)});
      digest.accept(VidarrPlugin.MAPPER.writeValueAsBytes(request));
    } catch (JacksonException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(request.hashCodeIgnoreAttempt(), stale);
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    if (stale) {
      return ActionState.ZOMBIE;
    }
    if (loaded) {
      return ActionState.SUCCEEDED;
    }
    if (rejected) {
      return ActionState.HALP;
    }
    final Set<String> throttled = services.isOverloaded(this.services);
    if (!throttled.isEmpty()) {
      errors = List.of("Services are unavailable: ", String.join(", ", throttled));
      return ActionState.THROTTLED;
    }
    final Optional<URI> url = owner.get().url();
    if (url.isEmpty()) {
      errors = List.of("Internal error: No Vidarr URL available");
      return ActionState.UNKNOWN;
    }
    try {
      final HttpResponse<String> response =
          VidarrPlugin.CLIENT.send(
              HttpRequest.newBuilder(url.get().resolve("/api/load-injected"))
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
          try {
            errors =
                VidarrPlugin.MAPPER
                    .readValue(response.body(), SubmitWorkflowResponseFailure.class)
                    .getErrors();
          } catch (JacksonException e) {
            errors = List.of(response.body(), e.getMessage());
          }
          rejected = true;
          return ActionState.HALP;
        case 409: // Conflicts with an existing workflow run
          errors = List.of("Conflict with existing workflow run in Vidarr: " + response.body());
          rejected = true;
          return ActionState.HALP;
        case 500:
          retryMinutes = Math.min(retryMinutes * 2, 60);
          errors = List.of(response.body());
          return ActionState.FAILED;
        case 507: // Too many load/unload requests queued in Vidarr
          errors = List.of(response.body());
          return ActionState.THROTTLED;
        default:
          retryMinutes = Math.min(retryMinutes * 2, 60);
          errors =
              List.of(
                  String.format("Unexpected HTTP response %d from server.", response.statusCode()));
          return ActionState.UNKNOWN;
      }
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
      errors = List.of(Objects.requireNonNullElse(e.getMessage(), e.getClass().getName()));
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
  public boolean search(Pattern query) {
    return query.matcher(request.getWorkflowVersion().getName()).matches()
        || query.matcher(request.getWorkflowVersion().getVersion()).matches()
        || request.getWorkflowRun().getExternalKeys().stream()
            .anyMatch(
                ek ->
                    query.matcher(ek.getProvider()).matches()
                        || query.matcher(ek.getId()).matches()
                        || ek.getVersions().entrySet().stream()
                            .anyMatch(
                                v ->
                                    query.matcher(v.getKey()).matches()
                                        || v.getValue().stream()
                                            .anyMatch(value -> query.matcher(value).matches())))
        || checkJson(request.getWorkflowRun().getArguments(), query)
        || checkJson(request.getWorkflowRun().getMetadata(), query)
        || checkJson(request.getWorkflowRun().getEngineParameters(), query);
  }

  @Override
  public Stream<String> tags() {
    return Stream.concat(
        super.tags(), Stream.of(loaded ? "vidarr-state:loaded" : "vidarr-state:attempt"));
  }

  @Override
  public ObjectNode toJson(JsonMapper mapper) {
    ObjectNode node = super.toJson(mapper);

    // Need to convertValue with the Mapper in order to use the Java Time parsing
    node.set("request", mapper.convertValue(request, JsonNode.class));
    // Bring these up for the 'Differences from Olive' sections
    node.get("request").asObject().set("arguments", request.getWorkflowRun().getArguments());
    node.get("request").asObject().set("metadata", request.getWorkflowRun().getMetadata());
    node.put("loadState", loaded ? "loaded" : "attempt");
    return node;
  }
}
