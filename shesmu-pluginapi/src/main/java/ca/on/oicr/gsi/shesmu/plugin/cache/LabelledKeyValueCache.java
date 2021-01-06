package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Store data that must be generated/fetched remotely and cache the results for a set period of
 * time.
 *
 * @param <K> the keys to use to lookup data in the cache
 * @param <L> the "name" associated with a key; this allows a key's lifecycle to be different from
 *     its data's
 * @param <V> the cached values
 */
public abstract class LabelledKeyValueCache<K, L, I, V>
    implements Owner, Iterable<Map.Entry<L, Record<V>>> {
  private class LabelledKeyValueUpdater implements Updater<I> {
    private final K key;
    private final L label;

    private LabelledKeyValueUpdater(K key, L label) {
      this.key = key;
      this.label = label;
    }

    @Override
    public Stream<Pair<String, String>> identifiers() {
      return Stream.of(new Pair<>("key", key.toString()), new Pair<>("label", label.toString()));
    }

    @Override
    public Owner owner() {
      return LabelledKeyValueCache.this;
    }

    @Override
    public I update(Instant lastModified) throws Exception {
      double cpuStart = Owner.CPU_TIME.getAsDouble();
      try (final AutoCloseable _ignored = fetchTime.start(name)) {
        return fetch(key, label, lastModified);
      } finally {
        fetchCpuTime.labels(name).observe(Owner.CPU_TIME.getAsDouble() - cpuStart);
      }
    }
  }

  private static final Map<String, SoftReference<LabelledKeyValueCache<?, ?, ?, ?>>> CACHES =
      new ConcurrentHashMap<>();
  private static final Gauge count =
      Gauge.build("shesmu_cache_lkv_item_count", "Number of items in a cache.")
          .labelNames("name")
          .register();
  private static final Histogram fetchCpuTime =
      Histogram.build()
          .buckets(1.0, 5.0, 10.0, 30.0, 60.0, 300.0, 600.0, 3600.0)
          .name("shesmu_cache_lkv_fetch_cpu")
          .help("The CPU Time to regenerate a cache value.")
          .labelNames("name")
          .register();
  private static final LatencyHistogram fetchTime =
      new LatencyHistogram(
          "shesmu_cache_lkv_fetch_wall",
          "The wall clock time to regenerate a cache value.",
          "name");
  private static final Gauge innerCount =
      Gauge.build("shesmu_cache_lkv_max_inner_count", "The largest collection stored in a cache.")
          .labelNames("name")
          .register();
  private static final Gauge ttlValue =
      Gauge.build("shesmu_cache_lkv_ttl", "The time-to-live of a cache, in minutes.")
          .labelNames("name")
          .register();

  public static Stream<? extends LabelledKeyValueCache<?, ?, ?, ?>> caches() {
    return CACHES.values().stream().map(SoftReference::get).filter(Objects::nonNull);
  }

  private long maxCount = 0;

  private final String name;

  private final RecordFactory<I, V> recordCtor;
  private final Map<L, Record<V>> records = new ConcurrentHashMap<>();
  private int ttl;

  /**
   * Create a new cache
   *
   * @param name the name, as presented to Prometheus
   * @param ttl the number of minutes an item will remain in cache
   */
  public LabelledKeyValueCache(String name, int ttl, RecordFactory<I, V> recordCtor) {
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
  protected abstract I fetch(K key, L label, Instant lastUpdated) throws Exception;

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
            label(key), label -> recordCtor.create(new LabelledKeyValueUpdater(key, label)));
    maxCount = Math.max(maxCount, record.collectionSize());
    innerCount.labels(name).set(maxCount);
    count.labels(name).set(records.size());
    return record.refresh(String.format("%s [key=%s]", name, key));
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
            label(key), label -> recordCtor.create(new LabelledKeyValueUpdater(key, label)));
    return record.readStale();
  }

  public final void invalidate(K key) {
    final Record<V> record = records.get(label(key));
    if (record != null) {
      record.invalidate();
    }
  }

  public void invalidateAll() {
    maxCount = 0;
    innerCount.labels(name).set(maxCount);
    records.values().forEach(Record::invalidate);
  }

  public final Iterator<Map.Entry<L, Record<V>>> iterator() {
    return records.entrySet().iterator();
  }

  /** The the fixed label associated with the specified key */
  protected abstract L label(K key);

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
