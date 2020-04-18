package ca.on.oicr.gsi.shesmu.plugin.input;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Provide data as a stream of JSON data
 *
 * <p>Shesmu will automatically use the standard streaming JSON unmarhsalling protocol to simplify
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
}
