package ca.on.oicr.gsi.shesmu.plugin.input;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Provide data as a stream of JSON data
 *
 * <p>Shesmu will automatically use the standard streaming JSON unmarshalling protocol to simplify
 * data extraction
 */
public interface JsonInputSource {
  /** An input source that contains no values (while being valid JSON) */
  JsonInputSource EMPTY = () -> new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8));
  /**
   * Get the stream.
   *
   * <p>Shesmu will manage caching the output, so this method will only be called when the cache
   * needs to be refreshed.
   *
   * @return a stream of JSON data in the expected input format
   * @throws Exception if an error accessing the data occurred the cache will serve stale data if an
   *     exception is thrown
   */
  InputStream fetch() throws Exception;

  /**
   * Allow dynamically changing the TTL for this cache.
   *
   * <p>This is only read when the cache has already expired and will be in effect until it expires.
   *
   * @return the new TTL, in minutes, or an empty optional to keep using the existing time
   */
  default Optional<Integer> ttl() {
    return Optional.empty();
  }
}
