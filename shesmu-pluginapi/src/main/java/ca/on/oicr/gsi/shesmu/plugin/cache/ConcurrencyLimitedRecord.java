package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.concurrent.Semaphore;

/**
 * Cache updater that throws an exception if too many concurrent updates are in progress
 *
 * @param <V> the value stored in the cache
 */
public class ConcurrencyLimitedRecord<V> implements Record<V> {
  public static <I, V> RecordFactory<I, V> limit(int maximum, RecordFactory<I, V> factory) {
    return new RecordFactory<I, V>() {
      private final Semaphore lock = new Semaphore(maximum);

      @Override
      public Record<V> create(Updater<I> updater) {
        return new ConcurrencyLimitedRecord<>(factory.create(updater), lock);
      }
    };
  }

  private final Record<V> inner;
  private final Semaphore lock;

  private ConcurrencyLimitedRecord(Record<V> inner, Semaphore lock) {
    this.inner = inner;
    this.lock = lock;
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
  public V refresh(String context) {
    if (!lock.tryAcquire()) {
      return inner.readStale();
    }
    try {
      return inner.refresh(context);
    } finally {
      lock.release();
    }
  }

  @Override
  public Updater<?> updater() {
    return inner.updater();
  }
}
