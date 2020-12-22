package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.sourceforge.seqware.common.model.IUSAttribute;
import net.sourceforge.seqware.common.model.WorkflowRun;

public class AnalysisState implements Comparable<AnalysisState> {
  private final Map<String, Set<String>> annotations;
  private final Set<Integer> fileSWIDSToRun;
  private final SortedSet<FileInfo> files;
  private final Set<String> knownSignatures;
  private final Instant lastModified;
  private final List<Pair<? extends LimsKey, Integer>> limsKeys;
  private final SortedSet<String> majorOliveVersion;
  private final boolean skipped;
  private final ActionState state;
  private final long workflowAccession;
  private final int workflowRunAccession;

  public AnalysisState(
      int workflowRunAccession,
      Supplier<WorkflowRun> run,
      IntFunction<net.sourceforge.seqware.common.model.LimsKey> getLimsKey,
      List<? extends AnalysisProvenance> source,
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
    skipped =
        source
            .stream()
            .anyMatch(
                ap ->
                    (ap.getSkip() != null && ap.getSkip())
                        || Stream.of(
                                ap.getFileAttributes(),
                                ap.getIusAttributes(),
                                ap.getWorkflowRunAttributes())
                            .anyMatch(a -> a.containsKey("skip")));
    // Get the LIMS keys; if the workflow has succeeded, then we know provisioning is complete and
    // we can gather all the LIMS keys from the output. If it has failed or is still running, then
    // LIMS keys may have been partially provisioned, which will be very confusing; in which case,
    // go thet the workflow that has the full set of LIMS keys at additional cost.
    final WorkflowRun workflowRun =
        state == ActionState.SUCCEEDED || source.stream().allMatch(ap -> ap.getFilePath() == null)
            ? null
            : run.get();
    if (workflowRun == null) {
      incrementSlowFetch.run();
    }
    limsKeys =
        (workflowRun == null
                ? source
                    .stream()
                    .flatMap(ap -> ap.getIusLimsKeys().stream())
                    .map(lk -> new Pair<>(new SimpleLimsKey(lk.getLimsKey()), lk.getIusSWID()))
                : workflowRun
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
                                  new Pair<>(
                                      new SimpleLimsKey(
                                          limsKey.getId(),
                                          limsKey.getProvider(),
                                          limsKey.getLastModified().toInstant(),
                                          limsKey.getVersion()),
                                      i.getSwAccession()));
                        }))
            .sorted(Comparator.comparing(Pair::first, WorkflowAction.LIMS_KEY_COMPARATOR))
            .distinct()
            .collect(Collectors.toList());
    knownSignatures =
        workflowRun == null
            ? source
                .stream()
                .flatMap(
                    ap ->
                        ap.getIusAttributes()
                            .getOrDefault("signature", Collections.emptySortedSet())
                            .stream())
                .collect(Collectors.toSet())
            : workflowRun
                .getIus()
                .stream()
                .flatMap(
                    i ->
                        i.getIusAttributes()
                            .stream()
                            .filter(a -> a.getTag().equals("signature"))
                            .map(IUSAttribute::getValue))
                .collect(Collectors.toSet());

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
                    Stream.of(s.getWorkflowRunAttributes(), s.getWorkflowAttributes())
                        .flatMap(m -> m.entrySet().stream())
                        .filter(e -> !e.getKey().equals(WorkflowAction.MAJOR_OLIVE_VERSION))
                        .flatMap(e -> e.getValue().stream().map(v -> new Pair<>(e.getKey(), v))))
            .collect(
                Collectors.groupingBy(
                    Pair::first, Collectors.mapping(Pair::second, Collectors.toSet())));
    files =
        source
            .stream()
            .filter(ap -> ap.getFileId() != null && ap.getFilePath() != null)
            .map(FileInfo::new)
            .collect(Collectors.toCollection(TreeSet::new));
  }

  public void addSeenLimsKeys(Set<Pair<String, String>> seenLimsKeys) {
    limsKeys
        .stream()
        .map(l -> new Pair<>(l.first().getProvider(), l.first().getVersion()))
        .forEach(seenLimsKeys::add);
  }

  /** Check how much this analysis record matches the data provided */
  public <L extends LimsKey> WorkflowRunMatch compare(
      LongStream workflowAccessions,
      String majorOliveVersion,
      FileMatchingPolicy fileMatchingPolicy,
      Set<Integer> inputFiles,
      List<L> inputLimsKeys,
      Map<L, Set<String>> signatures,
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
    int signatureEquivalent = 0;
    int provenanceIndex = 0;
    int inputIndex = 0;
    final Map<Integer, Set<String>> missingSignature = new HashMap<>();
    final Map<Integer, Pair<String, ZonedDateTime>> updatedVersions = new HashMap<>();
    while (inputIndex < inputLimsKeys.size() && provenanceIndex < limsKeys.size()) {
      final int comparison =
          WorkflowAction.LIMS_ID_COMPARATOR.compare(
              inputLimsKeys.get(inputIndex), limsKeys.get(provenanceIndex).first());
      if (comparison == 0) {
        // Match. Yay; advance both indices.
        // Is the version correct?
        if (inputLimsKeys
            .get(inputIndex)
            .getVersion()
            .equals(limsKeys.get(provenanceIndex).first().getVersion())) {
          matches++;
          // Okay, we matched versions, but does this have the correct signature. If not, make a
          // note of it and we will add the signature to IUS LIMS key later.
          final Set<String> desiredSignatures = signatures.get(inputLimsKeys.get(inputIndex));
          if (desiredSignatures != null && !knownSignatures.containsAll(desiredSignatures)) {
            missingSignature
                .computeIfAbsent(limsKeys.get(provenanceIndex).second(), k -> new TreeSet<>())
                .addAll(desiredSignatures);
          }
        } else {
          // We matched the provider and ID, but the version is different, is the signature the
          // olive gave us (if any) already attached to this IUS; we have this in a big set rather
          // than per IUS because of the complexity of having IUS records associated with multiple
          // analysis provenance records we've merged.
          final Set<String> desiredSignatures = signatures.get(inputLimsKeys.get(inputIndex));
          if (desiredSignatures != null && knownSignatures.containsAll(desiredSignatures)) {
            // The version might be stale, but the signature still matches, so make a note that we
            // have to update the LIMS key's version
            signatureEquivalent++;
            updatedVersions.put(
                limsKeys.get(provenanceIndex).second(),
                new Pair<>(
                    inputLimsKeys.get(inputIndex).getVersion(),
                    inputLimsKeys.get(inputIndex).getLastModified()));
          } else {
            // No matching signature, so it's just plain old stale records
            stale++;
          }
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
    // Remove any signatures already associated with this workflow run. This is probably not
    // necessary, but there's a possibility that signatures have been partially applied, so we're
    // going to be extra paranoid and remove any.
    for (final Set<String> requiredSignatures : missingSignature.values()) {
      requiredSignatures.removeAll(knownSignatures);
    }
    missingSignature.entrySet().removeIf(e -> e.getValue().isEmpty());
    final AnalysisComparison comparison;
    if (matches > 0
        && signatureEquivalent == 0
        && stale == 0
        && loneInputKeys == 0
        && loneProvenanceKeys == 0) {
      // There are matches and no left overs, we're an exact match
      comparison = AnalysisComparison.EXACT;
    } else if (signatureEquivalent > 0
        && stale == 0
        && loneInputKeys == 0
        && loneProvenanceKeys == 0) {
      // There are "matches" (thanks to signature) and no left overs, we're an fixable match
      comparison = AnalysisComparison.FIXABLE;
    } else if (inputFiles.isEmpty() && matches == 0 && stale == 0) {
      // If there were no input files, this must mean it is a root workflow, so there should always
      // be the LIMS key for the lane in common; if there are none, then these really don't match
      comparison = AnalysisComparison.DIFFERENT;
    } else {
      // There's some overlap, so we need a human
      comparison = AnalysisComparison.PARTIAL;
    }
    return new WorkflowRunMatch(
        comparison,
        this,
        loneProvenanceKeys > 0,
        loneInputKeys > 0,
        stale > 0,
        !this.fileSWIDSToRun.equals(inputFiles),
        missingSignature,
        updatedVersions);
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

  public Iterable<FileInfo> files() {
    return files;
  }

  public Instant lastModified() {
    return lastModified;
  }

  public boolean skipped() {
    return skipped;
  }

  public ActionState state() {
    return state;
  }

  public long workflowAccession() {
    return workflowAccession;
  }

  public int workflowRunAccession() {
    return workflowRunAccession;
  }
}
