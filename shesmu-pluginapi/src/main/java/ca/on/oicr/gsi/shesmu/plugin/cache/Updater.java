package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;

/** Service to update a record in cache */
public interface Updater<V> {
  /**
   * Perform the update
   *
   * @param lastModifed the last time the value was successfully pulled from cache
   */
  V update(Instant lastModifed) throws Exception;
}
