package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.time.Instant;

/**
 * A record stored in a cache of some kind
 *
 * @param <V> the type that can be retrieved from a cache
 */
public interface Record<V> {
  Gauge refreshStartTime =
      Gauge.build(
              "shesmu_cache_refresh_start_timestamp",
              "The UNIX time when a cache refresh was last started.")
          .labelNames("name")
          .register();
  Gauge refreshEndTime =
      Gauge.build(
              "shesmu_cache_refresh_end_timestamp",
              "The UNIX time when a cache refresh was finished.")
          .labelNames("name")
          .register();
  LatencyHistogram refreshLatency =
      new LatencyHistogram(
          "shesmu_cache_refresh_latency",
          "Attempted to refresh a value stored in cache, but the refresh failed.",
          "name");
  Counter staleRefreshError =
      Counter.build(
              "shesmu_cache_refresh_error",
              "Attempted to refresh a value stored in cache, but the refresh failed.")
          .labelNames("name")
          .register();

  /** The number of items stored in this cache record F */
  int collectionSize();

  /** Force the cached item to be reloaded on the next use. */
  void invalidate();

  /** Get the last time the item was updated */
  Instant lastUpdate();

  /** Get the current item value, but do not fetch */
  V readStale();

  /**
   * Get the current item value, fetching if necessary
   *
   * @param context information about the owner's cache to log if an exception occurs
   */
  V refresh(String context);

  /** The callback used by this record to fetch data */
  Updater<?> updater();
}
