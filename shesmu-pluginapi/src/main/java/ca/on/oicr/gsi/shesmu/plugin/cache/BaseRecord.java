package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Duration;
import java.time.Instant;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all
 */
public abstract class BaseRecord<R, S> implements Record<R> {
  private Instant fetchTime = Instant.EPOCH;
  protected final Updater<R> fetcher;
  private boolean initialState = true;
  private boolean regenerating;
  private S value;

  public BaseRecord(Updater<R> fetcher, S initialState) {
    this.fetcher = fetcher;
    this.value = initialState;
  }

  @Override
  public int collectionSize() {
    return collectionSize(value);
  }

  protected abstract int collectionSize(S state);

  @Override
  public final void invalidate() {
    fetchTime = Instant.EPOCH;
  }

  @Override
  public final Instant lastUpdate() {
    return fetchTime;
  }

  @Override
  public synchronized R readStale() {
    if (initialState) {
      throw new InitialCachePopulationException(fetcher.owner().name());
    }
    return unpack(value);
  }

  @Override
  public final R refresh(String context) {
    final boolean doRefresh;
    boolean shouldThrow;
    synchronized (this) {
      final var now = Instant.now();
      doRefresh =
          Duration.between(fetchTime, now).toMinutes() > fetcher.owner().ttl() && !regenerating;
      shouldThrow = initialState;
      if (doRefresh) {
        regenerating = true;
      }
    }
    if (doRefresh) {
      try (var timer = refreshLatency.start(fetcher.owner().name())) {
        var result = update(value, fetchTime);
        if (result != null) {
          synchronized (this) {
            value = result;
            fetchTime = Instant.now();
            initialState = false;
          }
          shouldThrow = false;
        }
      } catch (final Exception e) {
        System.err.printf("Exception occurred while refreshing cache %s is as follows:\n", context);
        e.printStackTrace();
        staleRefreshError.labels(fetcher.owner().name()).inc();
      } finally {
        synchronized (this) {
          regenerating = false;
        }
      }
    }
    if (shouldThrow) {
      throw new InitialCachePopulationException(fetcher.owner().name());
    }
    return unpack(value);
  }

  protected abstract R unpack(S state);

  protected abstract S update(S oldstate, Instant fetchTime) throws Exception;
}
