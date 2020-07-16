package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.runtime.subsample.Fixed;
import ca.on.oicr.gsi.shesmu.runtime.subsample.FixedWithConditions;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Squish;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Start;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubsamplerTest {

  public SubsamplerTest() {}

  @Test
  public void testData() throws IOException {
    System.err.println("Testing subsamplers");
    final Start<Integer> start = new Start<>();

    // Test for the Start<T> class
    // checking if the number of items is correct
    Assertions.assertEquals(
        0, start.subsample(Stream.of()).count(), "Start subsample program failed to run!");
    Assertions.assertEquals(
        0, start.subsample(Stream.of(5, 8, 9)).count(), "Start subsample program failed to run!");

    // Tests for the Fixed<T> class
    final Fixed<Integer> fixed = new Fixed<>(start, 6);
    // Empty Stream
    Assertions.assertEquals(
        0, fixed.subsample(Stream.of()).count(), "FixedEmpty subsample program failed to run!");
    // Stream with more data than the limit
    Assertions.assertEquals(
        Arrays.asList(1, 2, 3, 4, 5, 6),
        fixed.subsample(Stream.of(1, 2, 3, 4, 5, 6, 7)).collect(Collectors.toList()));
    // Stream with less data than the limit
    Assertions.assertEquals(
        Arrays.asList(1, 2, 3), fixed.subsample(Stream.of(1, 2, 3)).collect(Collectors.toList()));

    // Tests for the FixedWithConditions<T> class
    final Predicate<Integer> lesserThan = i -> (i < 10);
    final FixedWithConditions<Integer> conditioned =
        new FixedWithConditions<>(start, 8, lesserThan);
    Assertions.assertEquals(
        0,
        conditioned.subsample(Stream.of()).count(),
        "FixedWithConditionsEmpty subsample program failed to run!");
    Assertions.assertEquals(
        Arrays.asList(-9, -4, 0, 2, 6, 8, 9),
        conditioned
            .subsample(Stream.of(-9, -4, 0, 2, 6, 8, 9, 10, 2, 3, 6))
            .collect(Collectors.toList()));
    Assertions.assertEquals(
        Arrays.asList(-9, -4, 0, 2),
        conditioned.subsample(Stream.of(-9, -4, 0, 2)).collect(Collectors.toList()));
    Assertions.assertEquals(
        Collections.emptyList(),
        conditioned.subsample(Stream.of(10, 12, 15, 543)).collect(Collectors.toList()));

    // Tests for the Squish<T> class
    final Squish<Integer> squish = new Squish<>(start, 10);
    Assertions.assertEquals(
        0,
        conditioned.subsample(Stream.of()).count(),
        "SquishEmpty subsample program failed to run!");
    Assertions.assertEquals(
        Arrays.asList(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5),
        squish.subsample(Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5)).collect(Collectors.toList()));
    Assertions.assertEquals(
        Arrays.asList(-4, -2, 0, 2, 4, 6, 8, 10, 12, 14),
        squish
            .subsample(
                Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
            .collect(Collectors.toList()));
    Assertions.assertEquals(
        Arrays.asList(-4, -2, 0),
        squish.subsample(Stream.of(-4, -2, 0)).collect(Collectors.toList()));
    Assertions.assertEquals(
        Arrays.asList(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5),
        squish
            .subsample(Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
            .collect(Collectors.toList()));
  }
}
