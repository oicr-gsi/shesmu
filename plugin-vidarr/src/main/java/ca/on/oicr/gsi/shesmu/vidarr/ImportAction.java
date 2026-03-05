package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import ca.on.oicr.gsi.vidarr.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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
  private boolean stale;
  private int priority;
  final ImportRequest request = new ImportRequest();
  final Supplier<VidarrPlugin> owner;
  private final Set<String> services = new TreeSet<>(List.of("vidarr"));
  ImportState state = new ImportStateAttemptSubmit();
  List<String> errors = List.of();

  private final List<String> tags = new LinkedList<>();

  public ImportAction(Supplier<VidarrPlugin> owner, WorkflowDeclaration workflow) {
    super("vidarr-import");
    this.owner = owner;

    // TODO it'd be so sick if the object handled this itself
    UnloadedWorkflow adaptedWorkflow = new UnloadedWorkflow();
    adaptedWorkflow.setName(workflow.getName());
    adaptedWorkflow.setLabels(workflow.getLabels());
    request.setWorkflows(List.of(adaptedWorkflow));

    UnloadedWorkflowVersion adaptedVersion = new UnloadedWorkflowVersion();
    adaptedVersion.setName(workflow.getName());
    adaptedVersion.setVersion(workflow.getVersion());
    adaptedVersion.setWorkflow(workflow.getName());

    // TODO I don't know what to do about this one!
    // adaptedVersion.setAccessoryFiles();

    adaptedVersion.setLanguage(workflow.getLanguage());
    adaptedVersion.setOutputs(workflow.getMetadata());
    adaptedVersion.setParameters(workflow.getParameters());

    request.setWorkflowVersions(List.of(adaptedVersion));

    List<ProvenanceWorkflowRun<ExternalMultiVersionKey>> temp = new ArrayList<>();
    temp.add(new ProvenanceWorkflowRun<>());
    request.setWorkflowRuns(temp);

    priority = workflow.getName().hashCode() % 100;

    //    tags =
    //              List.of(
    //                      "vidarr-target:" + targetName,
    //                      "vidarr-workflow:" + workflowName,
    //                      "vidarr-workflow:" + workflowName + "/" + workflowVersion);
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
    request.getWorkflowRuns().get(0).setCreated(zdt);
  }

  @ActionParameter
  public void completed(Instant completed) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(completed, ZoneId.of("UTC"));
    request.getWorkflowRuns().get(0).setCompleted(zdt);
  }

  @ActionParameter
  public void modified(Instant modified) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(modified, ZoneId.of("UTC"));
    request.getWorkflowRuns().get(0).setModified(zdt);
  }

  @ActionParameter
  public void started(Instant started) {
    ZonedDateTime zdt = ZonedDateTime.ofInstant(started, ZoneId.of("UTC"));
    request.getWorkflowRuns().get(0).setStarted(zdt);
  }

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
      record.setExternalKeys((List<ExternalId>) a.get(3));
      record.setLabels((Map<String, String>) a.get(4));
      record.setMetatype((String) a.get(5));
      record.setModified(ZonedDateTime.ofInstant((Instant) a.get(6), ZoneId.of("UTC")));
      record.setPath((String) a.get(7));
      record.setSize((long) a.get(8));
      list.add(record);
    }
    request.getWorkflowRuns().get(0).setAnalysis(list);
  }

  @ActionParameter(name = "engine_parameters")
  public void engineParameters(JsonNode json) {
    request.getWorkflowRuns().get(0).setEngineParameters(json);
  }

  @SuppressWarnings("unchecked")
  @ActionParameter(name = "external_keys", type = "ao4id$sprovider$sstale$bversions$msas")
  public void externalKeys(Set<Tuple> values) {
    request
        .getWorkflowRuns()
        .get(0)
        .setExternalKeys(
            values.stream()
                .map(
                    value -> {
                      final ExternalMultiVersionKey externalKey = new ExternalMultiVersionKey();
                      externalKey.setId((String) value.get(0));
                      externalKey.setProvider((String) value.get(1));
                      stale |= (Boolean) value.get(2);
                      externalKey.setVersions((Map<String, Set<String>>) value.get(3));
                      return externalKey;
                    })
                .toList());
  }

  @ActionParameter(name = "input_files")
  public void inputFiles(Set<String> files) {
    request.getWorkflowRuns().get(0).setInputFiles(files.stream().toList());
  }

  @ActionParameter
  public void labels(Map<String, String> labels) {
    request
        .getWorkflowRuns()
        .get(0)
        .setLabels(VidarrPlugin.MAPPER.convertValue(labels, ObjectNode.class));
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
  public void generateUUID(Consumer<byte[]> digest) {}

  @Override
  public int hashCode() {
    return 0;
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
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 0;
  }

  @Override
  public boolean search(Pattern query) {
    return false;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    ObjectNode node = super.toJson(mapper);
    node.putPOJO("request", request);
    state.writeJson(mapper, node);
    return node;
  }
}
