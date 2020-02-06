package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.runtime.LaneSplittingGrouper;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class LaneSplittingGrouperTest {

  @Test
  public void testEmpty() {
    final Grouper<Pair<Long, String>, Set<String>> grouper =
        new LaneSplittingGrouper<>(
            i -> Collections.emptyList(),
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final Set<Integer> outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assert.assertEquals(new TreeSet<>(Arrays.asList(3, 1)), outputs);
  }

  @Test
  public void testJunkInSyntheticLane() {
    final List<List<Long>> partitions = Collections.singletonList(Arrays.asList(1L, 2L));
    final Grouper<Pair<Long, String>, Set<String>> grouper =
        new LaneSplittingGrouper<>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final Set<Integer> outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2"),
                    new Pair<>(2L, "C")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assert.assertEquals(Collections.emptySet(), outputs);
  }

  @Test
  public void testTwoJoinedPartitions() {
    final List<List<Long>> partitions = Collections.singletonList(Arrays.asList(1L, 2L));
    final Grouper<Pair<Long, String>, Set<String>> grouper =
        new LaneSplittingGrouper<>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final Set<Integer> outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assert.assertEquals(new TreeSet<>(Collections.singletonList(4)), outputs);
  }

  @Test
  public void testTwoJoinedPartitionsWithDuplicates() {
    final List<List<Long>> partitions = Collections.singletonList(Arrays.asList(1L, 2L));
    final Grouper<Pair<Long, String>, Set<String>> grouper =
        new LaneSplittingGrouper<>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final Set<Integer> outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2"),
                    new Pair<>(2L, "A"),
                    new Pair<>(2L, "B")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assert.assertEquals(new TreeSet<>(Collections.singletonList(4)), outputs);
  }

  @Test
  public void testTwoPartitions() {
    final List<List<Long>> partitions =
        Arrays.asList(Collections.singletonList(1L), Collections.singletonList(2L));
    final Grouper<Pair<Long, String>, Set<String>> grouper =
        new LaneSplittingGrouper<>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final Set<Integer> outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assert.assertEquals(new TreeSet<>(Arrays.asList(3, 1)), outputs);
  }
}
