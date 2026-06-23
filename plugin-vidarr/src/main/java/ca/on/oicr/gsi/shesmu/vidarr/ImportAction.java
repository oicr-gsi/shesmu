package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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

public class ImportAction extends VidarrAction {
  ImportRequest request;
  ImportState state = new ImportStateAttemptSubmit();

  public ImportAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-import", owner);

    this.request = ImportRequest.fromDeclaration(workflow);

    /*
    We don't have the full text of the workflow when creating an import from information available in an olive, and
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

  @ActionParameter(name = "output_provisioner")
  public void outputProvisioner(String name) {
    request.setOutputProvisionerName(name);
  }

  @ActionParameter(name = "new_output_dir")
  public void newOutputDir(String path) {
    request.setOutputPath(path);
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
    ImportAction o = (ImportAction) other;
    return stale == o.stale && Objects.equals(this.request, o.request);
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return state.commands().commands();
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
    return Objects.hash(request.hashCodeIgnoreAttempt(), stale);
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    if (stale) {
      return ActionState.ZOMBIE;
    }
    final Set<String> throttled = services.isOverloaded(this.services);
    if (!throttled.isEmpty()) {
      errors = List.of("Services are unavailable: ", String.join(", ", throttled));
      return ActionState.THROTTLED;
    }
    final ImportState.PerformResult result =
        owner
            .get()
            .url()
            .map(
                url -> {
                  try {
                    return state.perform(url, request, lastGeneratedByOlive, isOliveLive);
                  } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    return new ImportState.PerformResult(
                        List.of(e.getMessage()), ActionState.UNKNOWN, state);
                  }
                })
            .orElseGet(
                () ->
                    new ImportState.PerformResult(
                        List.of("Internal error: No Vidarr URL available"),
                        ActionState.UNKNOWN,
                        state));
    errors = result.errors();
    state = result.nextState();
    return result.actionState();
  }

  @Override
  public long retryMinutes() {
    return state.retryMinutes();
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
        || checkJson(request.getWorkflowRun().getEngineParameters(), query)
        || state.search(query);
  }

  @Override
  public OptionalInt sortKey(String key) {
    if (key.equals(SORT_KEY_ATTEMPT)) {
      if (state instanceof ImportStateAttemptSubmit) {
        return OptionalInt.of(((ImportStateAttemptSubmit) state).getAttempt());
      } else {
        return OptionalInt.of(request.getAttempt());
      }
    } else {
      return state.sortKey(key);
    }
  }

  @Override
  public Stream<String> tags() {
    return Stream.concat(super.tags(), state.tags());
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    ObjectNode node = super.toJson(mapper);

    // Need to convertValue with the Mapper in order to use the Java Time parsing
    node.set("request", mapper.convertValue(request, JsonNode.class));
    // Bring these up for the 'Differences from Olive' sections
    ((ObjectNode) node.get("request")).set("arguments", request.getWorkflowRun().getArguments());
    ((ObjectNode) node.get("request")).set("metadata", request.getWorkflowRun().getMetadata());
    state.writeJson(mapper, node);
    return node;
  }
}
