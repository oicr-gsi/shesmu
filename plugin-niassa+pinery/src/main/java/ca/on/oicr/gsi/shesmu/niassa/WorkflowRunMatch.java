package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.model.IUSAttribute;
import net.sourceforge.seqware.common.model.LimsKey;

public class WorkflowRunMatch implements Comparable<WorkflowRunMatch> {

  private static final Counter updateSignatures =
      Counter.build(
              "shesmu_niassa_update_signature",
              "The number of IUS LIMS keys that have had their signatures updated.")
          .labelNames("workflow")
          .register();
  private static final Counter updateVersionAlreadyNewer =
      Counter.build(
              "shesmu_niassa_update_version_already_newer",
              "The number of IUS LIMS keys that we would have updated their LIMS keys, but it was already newer.")
          .labelNames("workflow")
          .register();
  private static final Counter updateVersions =
      Counter.build(
              "shesmu_niassa_update_version",
              "The number of IUS LIMS keys that have had their versions updated.")
          .labelNames("workflow")
          .register();
  private final AnalysisComparison comparison;
  private final boolean extraLimsKeys;
  private final boolean fileSubset;
  private final boolean missingLimsKeys;
  private final Map<Integer, Set<String>> missingSignature;
  private final boolean stale;
  private final AnalysisState state;
  private final Map<Integer, Pair<String, ZonedDateTime>> updatedVersions;

  public WorkflowRunMatch(
      AnalysisComparison comparison,
      AnalysisState state,
      boolean extraLimsKeys,
      boolean missingLimsKeys,
      boolean stale,
      boolean fileSubset,
      Map<Integer, Set<String>> missingSignature,
      Map<Integer, Pair<String, ZonedDateTime>> updatedVersions) {
    this.comparison = comparison;
    this.state = state;
    this.extraLimsKeys = extraLimsKeys;
    this.missingLimsKeys = missingLimsKeys;
    this.stale = stale;
    this.fileSubset = fileSubset;
    this.missingSignature = missingSignature;
    this.updatedVersions = updatedVersions;
  }

  public WorkflowRunMatch(AnalysisComparison comparison, AnalysisState state) {
    this(
        comparison,
        state,
        false,
        false,
        false,
        false,
        Collections.emptyMap(),
        Collections.emptyMap());
  }

  public ActionState actionState() {
    // For a partially complete workflow, we might have missing LIMS keys because some of the files
    // are provisioned out and others are not, so the LIMS keys are in hiding. So, if it's still
    // running and the only problem is missing LIMS keys, consider it inflight.
    if (comparison == AnalysisComparison.EXACT
        || state.state() == ActionState.INFLIGHT && !extraLimsKeys && !stale && !fileSubset) {
      return state.state();
    } else {
      return ActionState.HALP;
    }
  }

  @Override
  public int compareTo(WorkflowRunMatch workflowRunMatch) {
    int result = Integer.compare(comparison.ordinal(), workflowRunMatch.comparison.ordinal());
    if (result == 0) {
      result = state.compareTo(workflowRunMatch.state);
    }
    return result;
  }

  public AnalysisComparison comparison() {
    return comparison;
  }

  /**
   * Flush any signatures to the Niassa database as annotations
   *
   * @param metadata the Niassa database
   */
  public void fixSignatures(long workflowAccession, Metadata metadata) {
    final Counter.Child counter = updateSignatures.labels(Long.toString(workflowAccession));
    for (final Map.Entry<Integer, Set<String>> signature : missingSignature.entrySet()) {
      // Write each signature that should be attached to the IUS LIMS key. There should always be
      // exactly one signature. If not, this will write two to the Niassa database (due to an
      // interesting design flaw in Niassa, it's possible to write two annotations with the same
      // tag, but subsequent updates will randomly erase one of them). I think the circumstances
      // under which you could get into a bad state are:
      // putting garbage for the signature in the olive instead of a SHA1, marking variables that
      // are not from LIMS as signable in the input data format, associating the same IUS with
      // multiple workflow runs (multiple times in the same WFR is fine).
      metadata.annotateIUS(
          signature.getKey(),
          signature
              .getValue()
              .stream()
              .map(
                  s -> {
                    final IUSAttribute attribute = new IUSAttribute();
                    attribute.setTag("signature");
                    attribute.setValue(s);
                    final ObjectNode logMessage = NiassaServer.MAPPER.createObjectNode();
                    logMessage.put("type", "signature");
                    logMessage.put("iusSWID", signature.getKey());
                    logMessage.put("signature", s);
                    try {
                      System.err.printf(
                          "LIMS KEY:%s\n", NiassaServer.MAPPER.writeValueAsString(logMessage));
                    } catch (JsonProcessingException e) {
                      // Ignore
                    }
                    return attribute;
                  })
              .collect(Collectors.toSet()));
      counter.inc();
    }
  }

  /**
   * Flush any LIMS key version updates to the Niassa database
   *
   * @param metadata the Niassa database
   */
  public void fixVersions(long workflowAccession, Metadata metadata) {
    final Counter.Child counter = updateVersions.labels(Long.toString(workflowAccession));
    final Counter.Child alreadyNewerCounter =
        updateVersionAlreadyNewer.labels(Long.toString(workflowAccession));
    for (final Map.Entry<Integer, Pair<String, ZonedDateTime>> version :
        updatedVersions.entrySet()) {
      final LimsKey limsKey = metadata.getLimsKeyFrom(version.getKey());
      final String oldValue = limsKey.getVersion();
      if (limsKey.getLastModified().isAfter(version.getValue().second())) {
        // We are in some kind of race condition where an action that has newer LIMS data than us
        // has already updated this LIMS key. We're just going to shut up about it.
        alreadyNewerCounter.inc();
        continue;
      }
      limsKey.setVersion(version.getValue().first());
      limsKey.setLastModified(version.getValue().second());
      metadata.updateLimsKey(limsKey);
      counter.inc();
      final ObjectNode logMessage = NiassaServer.MAPPER.createObjectNode();
      logMessage.put("type", "signature");
      logMessage.put("iusSWID", version.getKey());
      logMessage.put("oldVersion", oldValue);
      logMessage.put("newVersion", version.getValue().first());
      logMessage.put("newTimestamp", version.getValue().second().toInstant().toEpochMilli());
      try {
        System.err.printf("LIMS KEY:%s\n", NiassaServer.MAPPER.writeValueAsString(logMessage));
      } catch (JsonProcessingException e) {
        // Ignore
      }
    }
  }

  public AnalysisState state() {
    return state;
  }

  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode obj = mapper.createObjectNode();
    obj.put("workflowRunAccession", state.workflowRunAccession());
    obj.put("workflowAccession", state.workflowAccession());
    obj.put("state", state.state().name());
    obj.put("match", comparison.name());
    obj.put("extraLimsKeys", extraLimsKeys);
    obj.put("missingLimsKeys", missingLimsKeys);
    obj.put("skipped", state.skipped());
    obj.put("stale", stale);
    obj.put("fileSubset", fileSubset);
    return obj;
  }
}
