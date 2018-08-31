package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

import ca.on.oicr.gsi.shesmu.subsample.Fixed;
import ca.on.oicr.gsi.shesmu.subsample.FixedWithConditions;
import ca.on.oicr.gsi.shesmu.subsample.Squish;
import ca.on.oicr.gsi.shesmu.subsample.Start;

public class SubsamplerTest {

	public SubsamplerTest() {
	}

	@Test
	public void testData() throws IOException {
		System.err.println("Testing subsamplers");
		final Start<Integer> start = new Start<Integer>();

		// Test for the Start<T> class
		// checking if the number of items is correct
		Assert.assertTrue("Start subsample program failed to run!", start.subsample(Stream.of()).count() == 0);
		Assert.assertTrue("Start subsample program failed to run!", start.subsample(Stream.of(5, 8, 9)).count() == 0);

		// Tests for the Fixed<T> class
		final Fixed<Integer> fixed = new Fixed<Integer>(start, 6);
		// Empty Stream
		Assert.assertTrue("FixedEmpty subsample program failed to run!", fixed.subsample(Stream.of()).count() == 0);
		// Stream with more data than the limit
		Assert.assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6),
				fixed.subsample(Stream.of(1, 2, 3, 4, 5, 6, 7)).collect(Collectors.toList()));
		// Stream with less data than the limit
		Assert.assertEquals(Arrays.asList(1, 2, 3), fixed.subsample(Stream.of(1, 2, 3)).collect(Collectors.toList()));

		// Tests for the FixedWithConditions<T> class
		final Predicate<Integer> lesserThan = i -> (i < 10);
		final FixedWithConditions<Integer> conditioned = new FixedWithConditions<Integer>(start, 8, lesserThan);
		Assert.assertTrue("FixedWithConditionsEmpty subsample program failed to run!",
				conditioned.subsample(Stream.of()).count() == 0);
		Assert.assertEquals(Arrays.asList(-9, -4, 0, 2, 6, 8, 9),
				conditioned.subsample(Stream.of(-9, -4, 0, 2, 6, 8, 9, 10, 2, 3, 6)).collect(Collectors.toList()));
		Assert.assertEquals(Arrays.asList(-9, -4, 0, 2),
				conditioned.subsample(Stream.of(-9, -4, 0, 2)).collect(Collectors.toList()));
		Assert.assertEquals(Arrays.asList(),
				conditioned.subsample(Stream.of(10, 12, 15, 543)).collect(Collectors.toList()));

		// Tests for the Squish<T> class
		final Squish<Integer> squish = new Squish<Integer>(start, 10);
		Assert.assertTrue("SquishEmpty subsample program failed to run!",
				conditioned.subsample(Stream.of()).count() == 0);
		Assert.assertEquals(Arrays.asList(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5),
				squish.subsample(Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5)).collect(Collectors.toList()));
		Assert.assertEquals(Arrays.asList(-4, -2, 0, 2, 4, 6, 8, 10, 12, 14),
				squish.subsample(Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
						.collect(Collectors.toList()));
		Assert.assertEquals(Arrays.asList(-4, -2, 0),
				squish.subsample(Stream.of(-4, -2, 0)).collect(Collectors.toList()));
		Assert.assertEquals(Arrays.asList(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5), squish
				.subsample(Stream.of(-4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)).collect(Collectors.toList()));

	}

}
