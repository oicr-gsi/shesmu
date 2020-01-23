package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import io.prometheus.client.Counter;
import java.time.Instant;

/**
 * A record stored in a cache of some kind
 *
 * @param <V> the type of cached item
 */
public interface Record<V> {
  public static final LatencyHistogram refreshLatency =
      new LatencyHistogram(
          "shesmu_cache_refresh_latency",
          "Attempted to refresh a value stored in cache, but the refresh failed.",
          "name");
  public static final Counter staleRefreshError =
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

  /** Get the current item value, fetching if necessary */
  V refresh();
}
