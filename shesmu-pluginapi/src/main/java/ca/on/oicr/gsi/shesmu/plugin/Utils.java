package ca.on.oicr.gsi.shesmu.plugin;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Miscellaneous utility functions
 *
 * <p>These are not especially Shesmu-related
 */
public class Utils {

  private Utils() {}

  /**
   * Combine two optionals, taking the other if one is empty, or combining the values if both exist
   */
  public static <T> Optional<T> merge(
      Optional<T> left, Optional<T> right, BiFunction<? super T, ? super T, ? extends T> merge) {
    if (left.isPresent() && right.isPresent()) {
      return Optional.of(merge.apply(left.get(), right.get()));
    }
    if (left.isPresent()) {
      return left;
    }
    return right;
  }
  /** Stream an iterable object */
  public static <T> Stream<T> stream(Iterable<T> iterable) {
    return stream(iterable.spliterator());
  }

  /** Convert an iterator to a stream */
  public static <T> Stream<T> stream(Iterator<T> iterator) {
    return stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED));
  }

  /** Convert a spliterator to a stream */
  public static <T> Stream<T> stream(Spliterator<T> spliterator) {
    return StreamSupport.stream(spliterator, false);
  }
}
