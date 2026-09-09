package ca.on.oicr.gsi.shesmu.plugin;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Miscellaneous utility functions
 *
 * <p>These are not especially Shesmu-related
 */
public class Utils {

  public static String bytesToHex(byte[] bytes) {
    var hexChars = new char[bytes.length * 2];
    for (var j = 0; j < bytes.length; j++) {
      var v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  public static byte[] toBytes(long x) {
    final var buffer = ByteBuffer.allocate(Long.BYTES);
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

  public static String get301LocationUrl(HttpResponse httpResponse, Definer definer) {
    HttpHeaders headers = httpResponse.headers();
    try {
      URI urlFrom301 = new URI(headers.map().get("location").get(0));
      String newUrl = new URI(urlFrom301.getScheme() + "://" + urlFrom301.getHost()).toString();
      definer.log(
          "Got 301 when configuring plugin, updating URL to " + newUrl,
          LogLevel.WARN,
          new TreeMap<>());
      return newUrl;
    } catch (URISyntaxException use) {
      definer.log(
          "Got 301 when configuring plugin, HTTP location header invalid: "
              + headers.map().get("location").get(0),
          LogLevel.ERROR,
          new TreeMap<>());
      return null;
    }
  }

  public static HttpRequest httpGet(String uri, Optional<Integer> timeout) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri));
    if (timeout.isPresent()) builder.timeout(Duration.ofMinutes(timeout.get()));
    return builder.GET().build();
  }

  /** The longest any one exception's message may be before it gets abbreviated */
  private static final int MAX_CAUSE_MESSAGE_LENGTH = 200;

  private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

  /**
   * Flatten an exception message to a single bounded line
   *
   * <p>Jackson's messages are the reason this is necessary: they run to several lines and tack on a
   * source location full of the names of the features that were switched off while producing it.
   */
  private static String flattenMessage(String message) {
    final String flattened = WHITESPACE_RUN.matcher(message).replaceAll(" ").trim();
    return flattened.length() <= MAX_CAUSE_MESSAGE_LENGTH
        ? flattened
        : flattened.substring(0, MAX_CAUSE_MESSAGE_LENGTH) + "...";
  }

  /**
   * Describe an exception and all of its causes on a single line
   *
   * <p>The message on the outermost exception is frequently useless on its own; a truncated HTTP
   * response, for instance, arrives as a bare <code>closed</code> with the real explanation buried
   * several causes down.
   *
   * <p>The result goes into a log line and into the text shown for an unusable input format, so
   * each message is flattened onto one line and abbreviated rather than being used verbatim.
   */
  public static String describeCauseChain(Throwable throwable) {
    final StringBuilder output = new StringBuilder();
    // Cause chains are allowed to be cyclic, so track what has already been printed
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = throwable;
        current != null && seen.add(current);
        current = current.getCause()) {
      if (!output.isEmpty()) {
        output.append(" caused by ");
      }
      output.append(current.getClass().getSimpleName());
      final String message = current.getMessage();
      if (message != null && !message.isBlank()) {
        output.append(": ").append(flattenMessage(message));
      }
    }
    return output.toString();
  }
}
