package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LaneSplittingGrouper<I, O> implements Grouper<I, O> {
  private final Function<Set<Long>, BiConsumer<O, I>> collectorForLanes;
  private final Function<I, List<List<Long>>> permittedMerges;
  private final Function<I, Boolean> isSample;
  private final Function<I, Long> laneNumber;

  public LaneSplittingGrouper(
      Function<I, List<List<Long>>> permittedMerges,
      Function<I, Boolean> isSample,
      Function<I, Long> laneNumber,
      Function<Set<Long>, BiConsumer<O, I>> collectorForLanes) {
    this.permittedMerges = permittedMerges;
    this.isSample = isSample;
    this.laneNumber = laneNumber;
    this.collectorForLanes = collectorForLanes;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    final Set<List<List<Long>>> permittedMergedLanes =
        inputs.stream().map(permittedMerges).collect(Collectors.toSet());
    if (permittedMergedLanes.size() > 1) {
      // Each sample has a different idea of what merges are permitted by the flowcell. This is no
      // bueno...
      return Stream.empty();
    }
    final Map<Long, Long> canMerge = new TreeMap<>();
    for (List<Long> mergableLanes : permittedMergedLanes.iterator().next()) {
      final OptionalLong target = mergableLanes.stream().mapToLong(Long::longValue).min();
      if (!target.isPresent()) {
        continue;
      }
      // Make all these lanes point to the lowest numbered lane in the group; if a lane has been
      // multiply assigned, drop this mess.
      for (final Long lane : mergableLanes) {
        if (canMerge.put(lane, target.getAsLong()) != null) {
          return Stream.empty();
        }
      }
    }

    // Bin input by the lane it claims to be
    final Map<Long, List<I>> groups =
        inputs
            .stream()
            .collect(Collectors.groupingBy(laneNumber, TreeMap::new, Collectors.toList()));
    // If we weren't given any useful flow cell information (i.e., no mergable groups), just assume
    // everything goes in the same lane.
    final Function<Long, Long> targetLane =
        canMerge.isEmpty() ? Function.identity() : canMerge::get;

    Set<Long> lanes = null;
    final Deque<Subgroup<I, O>> results = new ArrayDeque<>();
    for (Map.Entry<Long, List<I>> entry : groups.entrySet()) {
      final Long target = targetLane.apply(entry.getKey());
      // We are now given a lane that we don't know where to assign. Reject this whole thing.
      if (target == null) {
        return Stream.empty();
      }
      // First thing always goes in a new lane as does anything not in our current group
      if (results.isEmpty() || !lanes.contains(target)) {

        lanes = new TreeSet<>();
        results.add(new Subgroup<>(collectorForLanes.apply(lanes)));
      } else if (!target.equals(entry.getKey())
          && entry.getValue().stream().anyMatch(isSample::apply)) {
        // If this isn't the first lane in a group, it should have only samples
        return Stream.empty();
      }
      lanes.add(entry.getKey());
      results.getLast().addAll(entry.getValue());
    }
    return results.stream();
  }
}
