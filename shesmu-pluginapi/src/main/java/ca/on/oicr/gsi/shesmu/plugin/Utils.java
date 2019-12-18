package ca.on.oicr.gsi.shesmu.plugin;

import java.nio.ByteBuffer;
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

  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static byte[] toBytes(long x) {
    final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(x);
    return buffer.array();
  }
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

  /** Convert an iterator to a stream */
  public static <T> Stream<T> stream(Iterator<T> iterator) {
    return stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED));
  }

  /** Convert a spliterator to a stream */
  public static <T> Stream<T> stream(Spliterator<T> spliterator) {
    return StreamSupport.stream(spliterator, false);
  }

  /** Stream an iterable object */
  public static <T> Stream<T> stream(Iterable<T> iterable) {
    return stream(iterable.spliterator());
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  private Utils() {}
}
