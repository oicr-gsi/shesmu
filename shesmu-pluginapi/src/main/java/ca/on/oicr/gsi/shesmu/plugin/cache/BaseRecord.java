package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Duration;
import java.time.Instant;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all
 */
public abstract class BaseRecord<R, S> implements Record<R> {
  private Instant fetchTime = Instant.EPOCH;
  private boolean initialState = true;
  private final Owner owner;
  private boolean regenerating;
  private S value;

  public BaseRecord(Owner owner, S initialState) {
    this.owner = owner;
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
  public final R refresh() {
    final boolean doRefresh;
    boolean shouldThrow;
    synchronized (this) {
      final Instant now = Instant.now();
      doRefresh = Duration.between(fetchTime, now).toMinutes() > owner.ttl() && !regenerating;
      shouldThrow = initialState;
      if (doRefresh) {
        regenerating = true;
      }
    }
    if (doRefresh) {
      try (AutoCloseable timer = refreshLatency.start(owner.name())) {
        S result = update(value, fetchTime);
        if (result != null) {
          synchronized (this) {
            value = result;
            fetchTime = Instant.now();
            initialState = false;
          }
          shouldThrow = false;
        }
      } catch (final Exception e) {
        e.printStackTrace();
        staleRefreshError.labels(owner.name()).inc();
      } finally {
        synchronized (this) {
          regenerating = false;
        }
      }
    }
    if (shouldThrow) {
      throw new InitialCachePopulationException();
    }
    return unpack(value);
  }

  protected abstract R unpack(S state);

  protected abstract S update(S oldstate, Instant fetchTime) throws Exception;
}
