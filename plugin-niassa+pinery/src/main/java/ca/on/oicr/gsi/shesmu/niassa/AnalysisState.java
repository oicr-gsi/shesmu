package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class AnalysisState implements Comparable<AnalysisState> {
  private final String fileSWIDSToRun;
  private final Instant lastModified;
  private final List<LimsKey> limsKeys;
  private final SortedSet<String> majorOliveVersion;
  private final ActionState state;
  private final long workflowAccession;
  private final int workflowRunAccession;

  public AnalysisState(Pair<Integer, Integer> accessions, List<AnalysisProvenance> source) {
    fileSWIDSToRun =
        source
            .stream()
            .flatMap(s -> s.getWorkflowRunInputFileIds().stream())
            .sorted()
            .distinct()
            .map(Object::toString)
            .collect(Collectors.joining(","));
    limsKeys =
        source
            .stream()
            .flatMap(s -> s.getIusLimsKeys().stream())
            .map(IusLimsKey::getLimsKey)
            .sorted(WorkflowAction.LIMS_KEY_COMPARATOR)
            .distinct()
            .collect(Collectors.toList());
    state =
        source
            .stream()
            .map(AnalysisProvenance::getWorkflowRunStatus)
            .map(NiassaServer::processingStateToActionState)
            .sorted(Comparator.comparing(ActionState::sortPriority))
            .findFirst()
            .get();
    workflowRunAccession = accessions.first();
    workflowAccession = accessions.second();
    lastModified =
        source
            .stream()
            .map(ap -> ap.getLastModified().toInstant())
            .max(Comparator.naturalOrder())
            .orElse(null);
    majorOliveVersion =
        source
            .stream()
            .flatMap(
                s ->
                    s.getWorkflowRunAttributes()
                        .getOrDefault("magic", Collections.emptySortedSet())
                        .stream())
            .collect(Collectors.toCollection(TreeSet::new));
  }

  public boolean compare(
      LongStream workflowAccessions,
      String majorOliveVersion,
      String fileAccessions,
      List<? extends LimsKey> limsKeys) {
    if (workflowAccessions.noneMatch(a -> workflowAccession == a)
        || !this.majorOliveVersion.isEmpty() && !this.majorOliveVersion.contains(majorOliveVersion)
        || !this.fileSWIDSToRun.equals(fileAccessions)
        || this.limsKeys.size() != limsKeys.size()) {
      return false;
    }
    for (int i = 0; i < limsKeys.size(); i++) {
      final LimsKey a = this.limsKeys.get(i);
      final LimsKey b = limsKeys.get(i);
      if (!a.getProvider().equals(b.getProvider())
          || !a.getId().equals(b.getId())
          || !a.getVersion().equals(b.getVersion())
          || !a.getLastModified().toInstant().equals(b.getLastModified().toInstant())) {
        return false;
      }
    }
    return true;
  }

  /** Sort so that the latest, most successful run is first. */
  @Override
  public int compareTo(AnalysisState other) {
    int comparison = Integer.compare(state.sortPriority(), other.state.sortPriority());
    if (comparison == 0) {
      comparison = Integer.compare(other.workflowRunAccession, workflowRunAccession);
    }
    return comparison;
  }

  public Instant lastModified() {
    return lastModified;
  }

  public ActionState state() {
    return state;
  }

  public int workflowRunAccession() {
    return workflowRunAccession;
  }
}
