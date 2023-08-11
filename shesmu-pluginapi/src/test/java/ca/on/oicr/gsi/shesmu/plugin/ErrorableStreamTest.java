package ca.on.oicr.gsi.shesmu.plugin;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ErrorableStreamTest {
  @Test
  public void testConstruct() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3), false);
    Assertions.assertFalse(testStream.isOk());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(4, 5, 6));
    Assertions.assertTrue(testStream2.isOk());

    ErrorableStream<Integer> testStream3 = new ErrorableStream<>(testStream);
    Assertions.assertFalse(testStream3.isOk());

    ErrorableStream<Integer> testStream4 = new ErrorableStream<>(testStream2);
    Assertions.assertTrue(testStream4.isOk());
  }

  @Test
  public void testInvalidate() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3));
    Assertions.assertTrue(testStream.isOk());
    testStream.invalidate();
    Assertions.assertFalse(testStream.isOk());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(4, 5, 6), false);
    Assertions.assertFalse(testStream2.isOk());
    testStream2.invalidate();
    Assertions.assertFalse(testStream2.isOk());
  }

  @Test
  public void testFilter() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream =
        (ErrorableStream<Integer>) testStream.filter(i -> i % 2 == 0);
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {2, 4}, resultStream.toArray());
  }

  @Test
  public void testMap() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.map(i -> i * 2);
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {2, 4, 6, 8}, resultStream.toArray());
  }

  @Test
  public void testMapToInt() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(UnsupportedOperationException.class, () -> testStream.mapToInt(i -> i));
  }

  @Test
  public void testMapToLong() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> testStream.mapToLong(i -> i));
  }

  @Test
  public void testMapToDouble() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class, () -> testStream.mapToDouble(i -> i));
  }

  @Test
  public void testFlatmap() {
    Stream<Integer> a = Stream.of(1, 2, 3);
    ErrorableStream<Integer> b = new ErrorableStream<>(Stream.of(4, 5, 6), false);
    ErrorableStream<Stream<Integer>> c = new ErrorableStream<>(Stream.of(a, b));

    Stream<Integer> result = c.flatMap(s -> s.map(i -> i * 2));
    Assertions.assertFalse(((ErrorableStream<Integer>) result).isOk());
    Assertions.assertArrayEquals(new Integer[] {2, 4, 6, 8, 10, 12}, result.toArray());

    List<Integer> d = List.of(1, 2, 3);
    Set<Integer> e = Set.of(4, 5, 6);
    ErrorableStream<Collection<Integer>> f = new ErrorableStream<>(Stream.of(d, e));

    Stream<Integer> result2 = f.flatMap(Collection::stream);
    Assertions.assertTrue(((ErrorableStream<Integer>) result2).isOk());
    Integer[] expected = new Integer[] {1, 2, 3, 4, 5, 6};
    List<Integer> ints = result2.toList();
    for (int i : expected) {
      Assertions.assertTrue(ints.contains(i));
    }
  }

  @Test
  public void testFlatmapToInt() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.flatMapToInt(i -> Stream.empty().mapToInt(j -> (Integer) j)));
  }

  @Test
  public void testFlatmapToLong() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.flatMapToLong(i -> Stream.empty().mapToLong(j -> (Long) j)));
  }

  @Test
  public void testFlatmapToDouble() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.flatMapToDouble(i -> Stream.empty().mapToDouble(j -> (Double) j)));
  }

  @Test
  public void testDistinct() {
    ErrorableStream<Integer> testStream =
        new ErrorableStream<>(Stream.of(1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 1), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.distinct();
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, resultStream.toArray());
  }

  @Test
  public void testSorted() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.sorted();
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, resultStream.toArray());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    ErrorableStream<Integer> resultStream2 =
        (ErrorableStream<Integer>) testStream2.sorted(Comparator.reverseOrder());
    Assertions.assertFalse(resultStream2.isOk());
    Assertions.assertArrayEquals(new Integer[] {4, 3, 2, 1}, resultStream2.toArray());
  }

  @Test
  public void testLimit() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.limit(3);
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3}, resultStream.toArray());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream2 = (ErrorableStream<Integer>) testStream2.limit(5);
    Assertions.assertFalse(resultStream2.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, resultStream2.toArray());
  }

  @Test
  public void testSkip() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4, 5), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.skip(3);
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {4, 5}, resultStream.toArray());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream2 = (ErrorableStream<Integer>) testStream2.skip(5);
    Assertions.assertFalse(resultStream2.isOk());
    Assertions.assertEquals(0, resultStream2.count());
  }

  @Test
  public void testForEach() {
    List<Integer> list = new ArrayList<>();
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    testStream.forEach(list::add);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, list.toArray());
  }

  @Test
  public void testToArray() {
    Integer[] expected = new Integer[] {1, 2, 3, 4};
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Object[] result = testStream.toArray();
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertArrayEquals(expected, result);
  }

  @Test
  public void testReduce() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Integer sum = testStream.reduce(0, Integer::sum);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(10, sum);

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Optional<Integer> sum2 = testStream2.reduce(Integer::sum);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(10, sum2.orElse(0));
  }

  @Test
  public void testCollect() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    List<Integer> result = testStream.collect(Collectors.toList());
    List<Integer> expected = new ArrayList<>();
    expected.add(1);
    expected.add(2);
    expected.add(3);
    expected.add(4);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(expected, result);

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    List<Integer> result2 = testStream2.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(expected, result2);
  }

  @Test
  public void testMin() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(1, testStream.min(Comparator.naturalOrder()).orElse(0));

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(4, testStream2.min(Comparator.reverseOrder()).orElse(0));
  }

  @Test
  public void testMax() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(4, testStream.max(Comparator.naturalOrder()).orElse(0));

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(1, testStream2.max(Comparator.reverseOrder()).orElse(0));
  }

  @Test
  public void testCount() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(4, testStream.count());
  }

  @Test
  public void testAnyMatch() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(testStream.anyMatch(i -> i == 3));
  }

  @Test
  public void testAllMatch() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(2, 4, 6, 8), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(testStream.allMatch(i -> i % 2 == 0));
  }

  @Test
  public void testNoneMatch() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(2, 4, 6, 8), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(testStream.noneMatch(i -> i % 2 == 1));
  }

  @Test
  public void testFindFirst() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertEquals(3, testStream.findFirst().orElse(0));

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.empty(), false);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(Optional.empty(), testStream2.findFirst());
  }

  @Test
  public void testFindAny() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(3, 2, 4, 1), false);
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(testStream.findAny().isPresent());

    ErrorableStream<Integer> testStream2 = new ErrorableStream<>(Stream.empty(), false);
    Assertions.assertFalse(testStream2.isOk());
    Assertions.assertEquals(Optional.empty(), testStream2.findAny());
  }

  @Test
  public void testIterator() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Integer[] expected = new Integer[] {1, 2, 3, 4};
    Iterator<Integer> result = testStream.iterator();

    Assertions.assertFalse(testStream.isOk());
    for (int i = 0; i < expected.length; i++) {
      Assertions.assertEquals(expected[i], result.next());
    }
  }

  @Test
  public void testSpliterator() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Spliterator<Integer> spliterator = testStream.spliterator();
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(spliterator.hasCharacteristics(Spliterator.SIZED));
  }

  @Test
  public void testIsParallel() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertFalse(testStream.isParallel());
    Assertions.assertFalse(testStream.isOk());
  }

  @Test
  public void testSequential() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.sequential();
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, resultStream.toArray());
  }

  @Test
  public void testParallel() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.parallel();
    Assertions.assertFalse(resultStream.isOk());
    Assertions.assertTrue(resultStream.isParallel());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, resultStream.toArray());
  }

  @Test
  public void testUnordered() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> resultStream = (ErrorableStream<Integer>) testStream.unordered();
    List<Integer> results = resultStream.toList();
    Assertions.assertFalse(resultStream.isOk());
    for (int i = 1; i <= 4; i++) {
      Assertions.assertTrue(results.contains(i));
    }
  }

  @Test
  public void testOnClose() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    AtomicBoolean b = new AtomicBoolean(false);
    testStream = (ErrorableStream<Integer>) testStream.onClose(() -> b.set(true));
    testStream.close();
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertTrue(b.get());
  }

  @Test
  public void testClose() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    testStream.close();
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertThrows(Exception.class, testStream::count);
  }

  @Test
  public void testMapMulti() {
    ErrorableStream<Number> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 3.14, 4), false);

    /* from https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/Stream.html#mapMulti(java.util.function.BiConsumer) */
    Object[] ints =
        testStream
            .<Integer>mapMulti(
                (number, consumer) -> {
                  if (number instanceof Integer i) consumer.accept(i);
                })
            .toArray();
    Assertions.assertFalse(testStream.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4}, ints);
  }

  @Test
  public void testMapMultiToInt() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.mapMultiToInt((a, b) -> IntStream.empty()));
  }

  @Test
  public void testMapMultiToLong() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.mapMultiToLong((a, b) -> LongStream.empty()));
  }

  @Test
  public void testMapMultiToDouble() {
    ErrorableStream<Integer> testStream = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    Assertions.assertThrows(
        UnsupportedOperationException.class,
        () -> testStream.mapMultiToDouble((a, b) -> DoubleStream.empty()));
  }

  @Test
  public void testConcatWithErrors() {
    ErrorableStream<Integer> a = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> b = new ErrorableStream<>(Stream.of(5, 6, 7, 8), false);
    Stream<Integer> c = Stream.of(9, 10), d = Stream.of(11, 12);

    ErrorableStream<Integer> result1 = ErrorableStream.concatWithErrors(a, b);
    Assertions.assertFalse(result1.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4, 5, 6, 7, 8}, result1.toArray());

    a = new ErrorableStream<>(Stream.of(1, 2, 3, 4), false);
    ErrorableStream<Integer> result2 = ErrorableStream.concatWithErrors(a, c);
    Assertions.assertFalse(result2.isOk());
    Assertions.assertArrayEquals(new Integer[] {1, 2, 3, 4, 9, 10}, result2.toArray());

    b = new ErrorableStream<>(Stream.of(5, 6, 7, 8), false);
    c = Stream.of(9, 10);
    ErrorableStream<Integer> result3 = ErrorableStream.concatWithErrors(c, b);
    Assertions.assertFalse(result3.isOk());
    Assertions.assertArrayEquals(new Integer[] {9, 10, 5, 6, 7, 8}, result3.toArray());

    c = Stream.of(9, 10);
    ErrorableStream<Integer> result4 = ErrorableStream.concatWithErrors(c, d);
    Assertions.assertTrue(result4.isOk());
    Assertions.assertArrayEquals(new Integer[] {9, 10, 11, 12}, result4.toArray());
  }
}
