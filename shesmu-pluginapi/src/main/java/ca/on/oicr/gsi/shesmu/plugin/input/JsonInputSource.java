package ca.on.oicr.gsi.shesmu.plugin.input;

import java.io.InputStream;

/**
 * Provide data as a stream of JSON data
 *
 * <p>Shesmu will automatically use the standard streaming JSON demarhsalling protocol to simplify
 * data extraction
 */
public interface JsonInputSource {
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
