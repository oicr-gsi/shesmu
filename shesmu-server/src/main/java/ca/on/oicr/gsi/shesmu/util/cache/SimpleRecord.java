package ca.on.oicr.gsi.shesmu.util.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Hold on to a single value
 *
 * <p>If the value could not be fetched, the previous value will be used instead.
 */
public final class SimpleRecord<V> implements Record<Optional<V>> {

  private final Updater<Optional<V>> fetcher;
  private Instant fetchTime = Instant.EPOCH;
  private final Owner owner;
  private Optional<V> value;

  public SimpleRecord(Owner owner, Updater<Optional<V>> fetcher) {
    this.owner = owner;
    this.fetcher = fetcher;
    this.value = Optional.empty();
  }

  @Override
  public void invalidate() {
    fetchTime = Instant.EPOCH;
  }

  @Override
  public Instant lastUpdate() {
    return fetchTime;
  }

  @Override
  public synchronized Optional<V> refresh() {
    final Instant now = Instant.now();
    if (Duration.between(fetchTime, now).toMinutes() > owner.ttl()) {
      try (AutoCloseable timer = refreshLatency.start(owner.name())) {
        final Optional<V> buffer = fetcher.update(fetchTime);
        if (buffer.isPresent()) {
          value = buffer;
          fetchTime = Instant.now();
        }
      } catch (final Exception e) {
        e.printStackTrace();
        staleRefreshError.labels(owner.name()).inc();
      }
    }
    return value;
  }

  @Override
  public int collectionSize() {
    return value.isPresent() ? 1 : 0;
  }
}
