package ca.on.oicr.gsi.shesmu.plugin.cache;

import io.prometheus.client.Gauge;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/**
 * Store data that must be generated/fetched remotely and cache the results for a set period of
 * time.
 *
 * @param <K> the keys to use to lookup data in the cache
 * @param <V> the cached values
 */
public abstract class KeyValueCache<K, V> implements Owner, Iterable<Map.Entry<K, Record<V>>> {
  public static Stream<? extends KeyValueCache<?, ?>> caches() {
    return CACHES.values().stream().map(SoftReference::get).filter(Objects::nonNull);
  }

  private static final Map<String, SoftReference<KeyValueCache<?, ?>>> CACHES =
      new ConcurrentHashMap<>();
  private static final Gauge count =
      Gauge.build("shesmu_cache_kv_item_count", "Number of items in a cache.")
          .labelNames("name")
          .register();

  private static final Gauge innerCount =
      Gauge.build("shesmu_cache_kv_max_inner_count", "The largest collection stored in a cache.")
          .labelNames("name")
          .register();
  private static final Gauge ttlValue =
      Gauge.build("shesmu_cache_kv_ttl", "The time-to-live of a cache, in minutes.")
          .labelNames("name")
          .register();
  private long maxCount = 0;

  private final String name;

  private final BiFunction<Owner, Updater<V>, Record<V>> recordCtor;
  private final Map<K, Record<V>> records = new ConcurrentHashMap<>();
  private int ttl;

  /**
   * Create a new cache
   *
   * @param name the name, as presented to Prometheus
   * @param ttl the number of minutes an item will remain in cache
   */
  public KeyValueCache(String name, int ttl, BiFunction<Owner, Updater<V>, Record<V>> recordCtor) {
    super();
    this.name = name;
    this.ttl = ttl;
    this.recordCtor = recordCtor;
    ttlValue.labels(name).set(ttl);
    CACHES.put(name, new SoftReference<>(this));
  }

  /**
   * Fetch an item from the remote service (or generate it)
   *
   * @param key the item to be requested
   * @param lastUpdated the last time the item was successfully fetched
   * @return the cached value
   * @throws Exception if an error occurs, the previous value will be retained
   */
  protected abstract V fetch(K key, Instant lastUpdated) throws Exception;

  /**
   * Get an item from cache
   *
   * @param key the key to use
   * @return the value, if it was possible to fetch; the value may be stale if the remote end-point
   *     is in an error state
   */
  public final V get(K key) {
    final Record<V> record =
        records.computeIfAbsent(
            key, k -> recordCtor.apply(this, lastModified -> fetch(k, lastModified)));
    maxCount = Math.max(maxCount, record.collectionSize());
    innerCount.labels(name).set(maxCount);
    count.labels(name).set(records.size());
    return record.refresh();
  }
  /**
   * Get an item from cache without updating it
   *
   * @param key the key to use
   * @return the last value that was fetched
   */
  public final V getStale(K key) {
    final Record<V> record =
        records.computeIfAbsent(
            key, k -> recordCtor.apply(this, lastModified -> fetch(k, lastModified)));
    return record.readStale();
  }

  public final void invalidate(K key) {
    final Record<V> record = records.get(key);
    if (record != null) {
      record.invalidate();
    }
  }

  public void invalidateAll() {
    maxCount = 0;
    innerCount.labels(name).set(maxCount);
    records.values().forEach(Record::invalidate);
  }

  public final Iterator<Map.Entry<K, Record<V>>> iterator() {
    return records.entrySet().iterator();
  }

  @Override
  public final String name() {
    return name;
  }

  @Override
  public final long ttl() {
    return ttl;
  }

  public final void ttl(int ttl) {
    this.ttl = ttl;
    ttlValue.labels(name).set(ttl);
  }
}
