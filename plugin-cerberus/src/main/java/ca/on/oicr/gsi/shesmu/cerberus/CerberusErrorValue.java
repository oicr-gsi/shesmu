package ca.on.oicr.gsi.shesmu.cerberus;

import static ca.on.oicr.gsi.shesmu.cerberus.BaseCerberusFileProvenanceRecord.labelsToMap;

import ca.on.oicr.gsi.cerberus.pinery.LimsProvenanceInfo;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.vidarr.api.ExternalKey;
import ca.on.oicr.gsi.vidarr.api.ProvenanceWorkflowRun;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CerberusErrorValue {

  private final Set<Tuple> availableLimsInfo;
  private final ProvenanceWorkflowRun<ExternalKey> workflow;
  private final Map<String, JsonNode> workflowRunLabels;

  public CerberusErrorValue(
      ProvenanceWorkflowRun<ExternalKey> workflow, Stream<LimsProvenanceInfo> limsInfo) {
    this.workflow = workflow;
    availableLimsInfo =
        limsInfo
            .map(
                info ->
                    new Tuple(
                        (long) info.formatRevision(),
                        info.lims().getProvenanceId(),
                        info.lims().getLastModified().toInstant(),
                        info.provider(),
                        info.lims().getVersion()))
            .collect(Collectors.toSet());
    workflowRunLabels = labelsToMap(workflow);
  }

  @ShesmuVariable(type = "ao5format_revision$iid$slast_modified$sprovider$sversion$s")
  public final Set<Tuple> available_lims() {
    return availableLimsInfo;
  }

  @ShesmuVariable
  public final Instant completed_date() {
    return workflow.getCompleted().toInstant();
  }

  @ShesmuVariable(type = "ao3id$sprovider$sversions$mss")
  public final Set<Tuple> external_keys() {
    return workflow.getExternalKeys().stream()
        .map(key -> new Tuple(key.getId(), key.getProvider(), key.getVersions()))
        .collect(Collectors.toSet());
  }

  @ShesmuVariable
  public final Set<String> input_files() {
    return new TreeSet<>(workflow.getInputFiles());
  }

  @ShesmuVariable
  public final String instance() {
    return workflow.getInstanceName();
  }

  @ShesmuVariable
  public final Instant timestamp() {
    return workflow.getCompleted().toInstant();
  }

  @ShesmuVariable
  public final String workflow() {
    return workflow.getWorkflowName();
  }

  @ShesmuVariable
  public final String workflow_accession() {
    return workflow.getWorkflowName() + "/" + workflow.getWorkflowVersion();
  }

  @ShesmuVariable
  public final String workflow_run_accession() {
    return "vidarr:" + workflow.getInstanceName() + "/" + workflow.getId();
  }

  @ShesmuVariable
  public final Map<String, JsonNode> workflow_run_labels() {
    return workflowRunLabels;
  }

  @ShesmuVariable(type = "t3iii")
  public final Tuple workflow_version() {
    return IUSUtils.parseWorkflowVersion(workflow.getWorkflowVersion())
        .orElse(IUSUtils.UNKNOWN_VERSION);
  }
}
