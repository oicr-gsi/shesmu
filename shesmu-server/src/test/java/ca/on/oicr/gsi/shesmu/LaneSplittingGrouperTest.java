package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.runtime.LaneSplittingGrouper;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LaneSplittingGrouperTest {

  @Test
  public void testEmpty() {
    final var grouper =
        new LaneSplittingGrouper<Pair<Long, String>, String, Set<String>>(
            i -> List.of(),
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final var outputs =
        grouper
            .group(
                List.of(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assertions.assertEquals(Set.of(), outputs);
  }

  @Test
  public void testJunkInSyntheticLane() {
    final var partitions = List.of(List.of(1L, 2L));
    final var grouper =
        new LaneSplittingGrouper<Pair<Long, String>, String, Set<String>>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final var outputs =
        grouper
            .group(
                List.of(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2"),
                    new Pair<>(2L, "C")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assertions.assertEquals(Set.of(), outputs);
  }

  @Test
  public void testTwoJoinedPartitions() {
    final var partitions = List.of(List.of(1L, 2L));
    final var grouper =
        new LaneSplittingGrouper<Pair<Long, String>, String, Set<String>>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final var outputs =
        grouper
            .group(
                List.of(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assertions.assertEquals(new TreeSet<>(List.of(4)), outputs);
  }

  @Test
  public void testTwoJoinedPartitionsWithDuplicates() {
    final var partitions = List.of(List.of(1L, 2L));
    final var grouper =
        new LaneSplittingGrouper<Pair<Long, String>, String, Set<String>>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final var outputs =
        grouper
            .group(
                List.of(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2"),
                    new Pair<>(2L, "A"),
                    new Pair<>(2L, "B")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assertions.assertEquals(new TreeSet<>(List.of(4)), outputs);
  }

  @Test
  public void testTwoPartitions() {
    final var partitions = List.of(List.of(1L), List.of(2L));
    final var grouper =
        new LaneSplittingGrouper<Pair<Long, String>, String, Set<String>>(
            i -> partitions,
            i ->
                Character.isAlphabetic(i.second().charAt(0))
                    ? Optional.of(i.second())
                    : Optional.empty(),
            Pair::first,
            g -> (o, i) -> o.add(i.second()));
    final var outputs =
        grouper
            .group(
                Arrays.asList(
                    new Pair<>(1L, "A"),
                    new Pair<>(1L, "B"),
                    new Pair<>(1L, "0"),
                    new Pair<>(2L, "2")))
            .map(s -> s.build(i -> new TreeSet<>()).size())
            .collect(Collectors.toSet());
    Assertions.assertEquals(new TreeSet<>(Arrays.asList(3, 1)), outputs);
  }
}
