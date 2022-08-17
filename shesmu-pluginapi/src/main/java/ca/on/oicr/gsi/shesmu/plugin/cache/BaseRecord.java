package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Duration;
import java.time.Instant;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all. The retrievable value may differ from the stored state.
 *
 * @param <V> the value retrievable from a cache based on the state
 * @param <S> the state that is stored in a cache
 */
public abstract class BaseRecord<V, S> implements Record<V> {
  private Instant fetchTime = Instant.EPOCH;
  protected final Updater<V> fetcher;
  private boolean initialState = true;
  private boolean regenerating;
  private S state;

  public BaseRecord(Updater<V> fetcher, S initialState) {
    this.fetcher = fetcher;
    this.state = initialState;
  }

  @Override
  public int collectionSize() {
    return collectionSize(state);
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
  public synchronized V readStale() {
    if (initialState) {
      throw new InitialCachePopulationException(fetcher.owner().name());
    }
    return unpack(state);
  }

  @Override
  public final V refresh(String context) {
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
        refreshStartTime.labels(fetcher.owner().name()).setToCurrentTime();
        S result = update(state, fetchTime);
        if (result != null) {
          synchronized (this) {
            state = result;
            fetchTime = Instant.now();
            refreshEndTime
                .labels(fetcher.owner().name())
                .setToCurrentTime(); // TODO why can't i use fetchTime
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
    return unpack(state);
  }

  protected abstract V unpack(S state);

  protected abstract S update(S oldstate, Instant fetchTime) throws Exception;
}
