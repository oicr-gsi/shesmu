package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class AnalysisState implements Comparable<AnalysisState> {
  private final String fileSWIDSToRun;
  private final List<LimsKey> limsKeys;
  private final SortedSet<String> majorOliveVersion;
  private final ActionState state;
  private final long workflowAccession;
  private final int workflowRunAccession;

  public int workflowRunAccession() {
    return workflowRunAccession;
  }

  public AnalysisState(AnalysisProvenance source) {
    fileSWIDSToRun =
        source
            .getWorkflowRunInputFileIds()
            .stream() //
            .map(Object::toString) //
            .collect(Collectors.joining(","));
    limsKeys =
        source
            .getIusLimsKeys()
            .stream() //
            .map(IusLimsKey::getLimsKey) //
            .sorted(WorkflowAction.LIMS_KEY_COMPARATOR) //
            .collect(Collectors.toList());
    state = NiassaServer.processingStateToActionState(source.getWorkflowRunStatus());
    workflowAccession = source.getWorkflowId();
    workflowRunAccession = source.getWorkflowRunId();
    majorOliveVersion =
        source.getWorkflowRunAttributes().getOrDefault("magic", Collections.emptySortedSet());
  }

  /** Sort so that the latest, most successful run is first. */
  @Override
  public int compareTo(AnalysisState other) {
    int comparison = state.sortPriority() - other.state.sortPriority();
    if (comparison == 0) {
      comparison = -Integer.compare(other.workflowRunAccession, workflowRunAccession);
    }
    return comparison;
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

  public ActionState state() {
    return state;
  }
}
