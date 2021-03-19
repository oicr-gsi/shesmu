package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.io.Closeable;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class ExclusiveRecord<T> implements Record<ExclusiveRecord<T>.Lifetime> {
  public final class Lifetime implements Closeable, Supplier<T> {
    private boolean released;
    private final T value;

    public Lifetime(T value) {
      this.value = value;
    }

    @Override
    public synchronized void close() {
      if (released) {
        throw new IllegalStateException("Attempt to release dead lifetime.");
      }
      lock.release();
      released = true;
    }

    @Override
    public synchronized T get() {
      if (released) {
        throw new IllegalStateException("Attempt to access dead lifetime.");
      }
      return value;
    }
  }

  public static <V> RecordFactory<V, ExclusiveRecord<V>.Lifetime> wrap(
      RecordFactory<V, V> recordCtor) {
    return updater -> new ExclusiveRecord<>(recordCtor.create(updater));
  }

  private final Record<T> inner;
  private final Semaphore lock = new Semaphore(1);

  public ExclusiveRecord(Record<T> inner) {
    this.inner = inner;
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
  public Lifetime readStale() {
    final var value = inner.readStale();
    lock.acquireUninterruptibly();
    return new Lifetime(value);
  }

  @Override
  public Lifetime refresh(String context) {
    final var value = inner.refresh(context);
    lock.acquireUninterruptibly();
    return new Lifetime(value);
  }

  @Override
  public Updater<?> updater() {
    return inner.updater();
  }
}
