package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.Pair;
import io.prometheus.client.Gauge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Create a record with a timeout on the length of time for a fetch
 *
 * @param <V> the type of the data in from the record
 */
public class TimeoutRecord<V> implements Record<V> {
  private static final Thread CLEANUP;
  private static final Semaphore LOCK = new Semaphore(1);
  private static final Map<Thread, Pair<Instant, String>> TIMEOUTS = new HashMap<>();
  private static final Gauge currentTimeout =
      Gauge.build("shesmu_cache_timeout_limit", "The timeout of this cache, in minutes.")
          .labelNames("cache")
          .register();
  private static final Gauge deadlineExceeded =
      Gauge.build(
              "shesmu_cache_timeout_deadline_exceeded",
              "The number of times the deadline for this cache has been hit.")
          .labelNames("cache")
          .register();

  static {
    CLEANUP =
        new Thread("cache-timeout-manager") {
          @Override
          public void run() {
            while (true) {
              final Instant now = Instant.now();
              LOCK.acquireUninterruptibly();
              for (final Thread deadThread :
                  TIMEOUTS
                      .entrySet()
                      .stream()
                      .filter(e -> e.getValue().first().isBefore(now))
                      .peek(e -> deadlineExceeded.labels(e.getValue().second()).inc())
                      .map(Map.Entry::getKey)
                      .collect(Collectors.toList())) {
                TIMEOUTS.remove(deadThread);
                deadThread.interrupt();
              }
              LOCK.release();
              try {
                Thread.sleep(60_000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
          }
        };
    CLEANUP.start();
  }
  /**
   * Build a new cache record type that limits how long a cache refresh can take
   *
   * <p>If it exceeds the deadline, it is interrupted and treated as a cache miss
   *
   * @param timeout the maximum number of minutes the fetch may take
   * @param constructor the record type to limit
   */
  public static <I, V> BiFunction<Owner, Updater<I>, Record<V>> limit(
      int timeout, BiFunction<Owner, Updater<I>, Record<V>> constructor) {
    return (owner, fetcher) ->
        new TimeoutRecord<>(owner.name(), constructor.apply(owner, fetcher), timeout);
  }

  private final Record<V> inner;
  private final int maxRuntime;
  private final String name;

  public TimeoutRecord(String name, Record<V> inner, int maxRuntime) {
    this.name = name;
    this.inner = inner;
    this.maxRuntime = maxRuntime;
    currentTimeout.labels(name).set(maxRuntime);
  }

  @Override
  public int collectionSize() {
    return inner.collectionSize();
  }

  @Override
  public void invalidate() {
    inner.invalidate();
  }

  @Override
  public Instant lastUpdate() {
    return inner.lastUpdate();
  }

  @Override
  public V readStale() {
    return inner.readStale();
  }

  @Override
  public V refresh() {
    try {
      LOCK.acquire();
      TIMEOUTS.put(
          Thread.currentThread(),
          new Pair<>(Instant.now().plus(maxRuntime, ChronoUnit.MINUTES), name));
      LOCK.release();
      final V result = inner.refresh();
      LOCK.acquire();
      TIMEOUTS.remove(Thread.currentThread());
      LOCK.release();
      return result;
    } catch (InterruptedException e) {
      return null;
    }
  }
}
