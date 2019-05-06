package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WorkflowRunMatch implements Comparable<WorkflowRunMatch> {

  private final AnalysisComparison comparison;
  private final boolean extraLimsKeys;
  private final boolean fileSubset;
  private final boolean missingLimsKeys;
  private final boolean stale;
  private final AnalysisState state;

  public WorkflowRunMatch(
      AnalysisComparison comparison,
      AnalysisState state,
      boolean extraLimsKeys,
      boolean missingLimsKeys,
      boolean stale,
      boolean fileSubset) {
    this.comparison = comparison;
    this.state = state;
    this.extraLimsKeys = extraLimsKeys;
    this.missingLimsKeys = missingLimsKeys;
    this.stale = stale;
    this.fileSubset = fileSubset;
  }

  public WorkflowRunMatch(AnalysisComparison comparison, AnalysisState state) {
    this(comparison, state, false, false, false, false);
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

  public AnalysisState state() {
    return state;
  }

  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode obj = mapper.createObjectNode();
    obj.put("workflowRunAccession", state.workflowRunAccession());
    obj.put("state", state.state().name());
    obj.put("match", comparison.name());
    obj.put("extraLimsKeys", extraLimsKeys);
    obj.put("missingLimsKeys", missingLimsKeys);
    obj.put("stale", stale);
    obj.put("fileSubset", fileSubset);
    return obj;
  }
}
