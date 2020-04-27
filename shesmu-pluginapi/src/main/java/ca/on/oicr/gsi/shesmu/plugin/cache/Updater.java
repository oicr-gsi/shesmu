package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.Pair;
import java.time.Instant;
import java.util.stream.Stream;

/** Service to update a record in cache */
public interface Updater<V> {
  /**
   * A list of identifiers for this updater
   *
   * <p>These are free-form, but useful for logging.
   */
  Stream<Pair<String, String>> identifiers();

  /** The cache that owns this record. */
  Owner owner();

  /**
   * Perform the update
   *
   * @param lastModifed the last time the value was successfully pulled from cache
   */
  V update(Instant lastModifed) throws Exception;
}
