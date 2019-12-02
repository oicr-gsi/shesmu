package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.sourceforge.seqware.common.model.WorkflowRun;

public class AnalysisState implements Comparable<AnalysisState> {
  private final Map<String, Set<String>> annotations;
  private final Set<Integer> fileSWIDSToRun;
  private final Instant lastModified;
  private final List<LimsKey> limsKeys;
  private final SortedSet<String> majorOliveVersion;
  private final ActionState state;
  private final long workflowAccession;
  private final int workflowRunAccession;

  public AnalysisState(
      int workflowRunAccession,
      Supplier<WorkflowRun> run,
      IntFunction<net.sourceforge.seqware.common.model.LimsKey> getLimsKey,
      List<AnalysisProvenance> source,
      Runnable incrementSlowFetch) {
    fileSWIDSToRun =
        source
            .stream()
            .flatMap(s -> s.getWorkflowRunInputFileIds().stream())
            .collect(Collectors.toCollection(TreeSet::new));
    state =
        source
            .stream()
            .map(AnalysisProvenance::getWorkflowRunStatus)
            .map(NiassaServer::processingStateToActionState)
            .min(Comparator.comparing(ActionState::sortPriority))
            .get();

    // Get the LIMS keys; if the workflow has succeeded, then we know provisioning is complete and
    // we can gather all the LIMS keys from the output. If it has failed or is still running, then
    // LIMS keys may have been partially provisioned, which will be very confusing; in which case,
    // go thet the workflow that has the full set of LIMS keys at additional cost.
    final boolean fastFetch =
        state == ActionState.SUCCEEDED || source.stream().allMatch(ap -> ap.getFilePath() == null);
    if (!fastFetch) {
      incrementSlowFetch.run();
    }
    limsKeys =
        (fastFetch
                ? source
                    .stream()
                    .flatMap(ap -> ap.getIusLimsKeys().stream())
                    .map(lk -> new SimpleLimsKey(lk.getLimsKey()))
                : run.get()
                    .getIus()
                    .stream()
                    .flatMap(
                        i -> {
                          // We forcibly load the LIMS key from Niassa because the WorkflowRun
                          // loader
                          // only populates the IUS ids and not the LIMS key within
                          final net.sourceforge.seqware.common.model.LimsKey limsKey =
                              getLimsKey.apply(i.getSwAccession());
                          return limsKey == null
                              ? Stream.empty()
                              : Stream.of(
                                  new SimpleLimsKey(
                                      limsKey.getId(),
                                      limsKey.getProvider(),
                                      limsKey.getLastModified().toInstant(),
                                      limsKey.getVersion()));
                        }))
            .sorted(WorkflowAction.LIMS_KEY_COMPARATOR)
            .distinct()
            .collect(Collectors.toList());

    this.workflowRunAccession = workflowRunAccession;
    workflowAccession = source.get(0).getWorkflowId();
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
                        .getOrDefault(
                            WorkflowAction.MAJOR_OLIVE_VERSION, Collections.emptySortedSet())
                        .stream())
            .collect(Collectors.toCollection(TreeSet::new));
    annotations =
        source
            .stream()
            .flatMap(
                s ->
                    s.getWorkflowRunAttributes()
                        .entrySet()
                        .stream()
                        .filter(e -> !e.getKey().equals(WorkflowAction.MAJOR_OLIVE_VERSION))
                        .flatMap(e -> e.getValue().stream().map(v -> new Pair<>(e.getKey(), v))))
            .collect(
                Collectors.groupingBy(
                    Pair::first, Collectors.mapping(Pair::second, Collectors.toSet())));
  }

  /** Check how much this analysis record matches the data provided */
  public WorkflowRunMatch compare(
      LongStream workflowAccessions,
      String majorOliveVersion,
      FileMatchingPolicy fileMatchingPolicy,
      Set<Integer> inputFiles,
      List<? extends LimsKey> inputLimsKeys,
      Map<String, String> annotations) {
    // If the WF, version or input files doesn't match, bail
    if (workflowAccessions.noneMatch(a -> workflowAccession == a)
        || !this.majorOliveVersion.isEmpty() && !this.majorOliveVersion.contains(majorOliveVersion)
        || !annotations
            .entrySet()
            .stream()
            .allMatch(
                e ->
                    this.annotations
                        .getOrDefault(e.getKey(), Collections.emptySet())
                        .contains(e.getValue()))
        || !fileMatchingPolicy.matches(inputFiles, fileSWIDSToRun)) {
      return new WorkflowRunMatch(AnalysisComparison.DIFFERENT, this);
    }

    // Check all the LIMS keys. We run along two ordered lists of LIMS keys and count the number
    // that match exactly and match partially
    int loneProvenanceKeys = limsKeys.size();
    int loneInputKeys = inputLimsKeys.size();
    int matches = 0;
    int stale = 0;
    int provenanceIndex = 0;
    int inputIndex = 0;
    while (inputIndex < inputLimsKeys.size() && provenanceIndex < limsKeys.size()) {
      final int comparison =
          WorkflowAction.LIMS_ID_COMPARATOR.compare(
              inputLimsKeys.get(inputIndex), limsKeys.get(provenanceIndex));
      if (comparison == 0) {
        // Match. Yay; advance both indices.
        if (inputLimsKeys
            .get(inputIndex)
            .getVersion()
            .equals(limsKeys.get(provenanceIndex).getVersion())) {
          matches++;
        } else {
          stale++;
        }
        loneProvenanceKeys--;
        provenanceIndex++;
        loneInputKeys--;
        inputIndex++;
      } else if (comparison < 0) {
        inputIndex++;
      } else {
        provenanceIndex++;
      }
    }
    final AnalysisComparison comparison;
    // There are matches and no left overs, we're an exact match
    if (matches > 0 && stale == 0 && loneInputKeys == 0 && loneProvenanceKeys == 0) {
      comparison = AnalysisComparison.EXACT;
      // If there were no input files, this must mean it is a root workflow, so there should always
      // be the LIMS key for the lane in common; if there are none, then these really don't match
    } else if (inputFiles.isEmpty() && matches == 0 && stale == 0) {
      comparison = AnalysisComparison.DIFFERENT;
      // There's some overlap, so we need a human
    } else {
      comparison = AnalysisComparison.PARTIAL;
    }
    return new WorkflowRunMatch(
        comparison,
        this,
        loneProvenanceKeys > 0,
        loneInputKeys > 0,
        stale > 0,
        !this.fileSWIDSToRun.equals(inputFiles));
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
