package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Store data that must be generated/fetched remotely and cache the results for a set period of
 * time.
 *
 * @param <S> the state that is stored in the cache
 * @param <V> the value retrievable from the cache based on the state
 */
public abstract class ValueCache<S, V> implements Owner {
  private static final Map<String, SoftReference<ValueCache<?, ?>>> CACHES =
      new ConcurrentHashMap<>();
  private static final Histogram fetchCpuTime =
      Histogram.build()
          .buckets(1.0, 5.0, 10.0, 30.0, 60.0, 300.0, 600.0, 3600.0)
          .name("shesmu_cache_v_fetch_cpu")
          .help("The CPU Time to regenerate a cache value.")
          .labelNames("name")
          .register();
  private static final LatencyHistogram fetchTime =
      new LatencyHistogram(
          "shesmu_cache_v_fetch_wall", "The wall clock time to regenerate a cache value.", "name");
  private static final Gauge innerCount =
      Gauge.build("shesmu_cache_v_max_inner_count", "The largest collection stored in a cache.")
          .labelNames("name")
          .register();
  private static final Gauge ttlValue =
      Gauge.build("shesmu_cache_v_ttl", "The time-to-live of a cache, in minutes.")
          .labelNames("name")
          .register();

  public static Stream<? extends ValueCache<?, ?>> caches() {
    return CACHES.values().stream().map(SoftReference::get).filter(Objects::nonNull);
  }

  private final String name;

  private int ttl;

  private final Record<V> value;

  /**
   * Create a new cache
   *
   * @param name the name, as presented to Prometheus
   * @param ttl the number of minutes an item will remain in cache
   */
  public ValueCache(String name, int ttl, RecordFactory<S, V> recordCtor) {
    super();
    this.name = name;
    this.ttl = ttl;
    this.value =
        recordCtor.create(
            new Updater<>() {

              @Override
              public Stream<Pair<String, String>> identifiers() {
                return Stream.empty();
              }

              @Override
              public Owner owner() {
                return ValueCache.this;
              }

              @Override
              public S update(Instant lastModifed) throws Exception {
                var cpuStart = Owner.CPU_TIME.getAsDouble();
                try (final var _ignored = fetchTime.start(name)) {
                  return fetch(lastModifed);
                } finally {
                  fetchCpuTime.labels(name).observe(Owner.CPU_TIME.getAsDouble() - cpuStart);
                }
              }
            });
    ttlValue.labels(name).set(ttl);
    // WARNING: Passing "this" outside constructor means that objects are
    // accessible before completely constructed
    CACHES.put(name, new SoftReference<>(this));
  }

  public int collectionSize() {
    return value.collectionSize();
  }

  /**
   * Fetch an item from the remote service (or generate it)
   *
   * @param lastUpdated the last time this item was successfully updated
   * @return the cached value
   */
  protected abstract S fetch(Instant lastUpdated) throws Exception;

  /**
   * Get an item from cache
   *
   * @return the value, if it was possible to fetch; the value may be stale if the remote end-point
   *     is in an error state
   */
  public V get() {
    final V item = value.refresh(name);
    innerCount.labels(name).set(value.collectionSize());
    return item;
  }
  /**
   * Get an item from cache, but do not update it
   *
   * @return the last value put in the cache
   */
  public V getStale() {
    return value.readStale();
  }

  public void invalidate() {
    value.invalidate();
  }

  public Instant lastUpdated() {
    return value.lastUpdate();
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
