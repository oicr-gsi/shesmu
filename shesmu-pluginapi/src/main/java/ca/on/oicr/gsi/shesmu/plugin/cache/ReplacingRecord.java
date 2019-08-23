package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all
 */
public final class ReplacingRecord<V> implements Record<Stream<V>> {
  private Instant fetchTime = Instant.EPOCH;
  private final Updater<Stream<V>> fetcher;
  private boolean initialState = true;
  private final Owner owner;
  private List<V> value;

  public ReplacingRecord(Owner owner, Updater<Stream<V>> fetcher) {
    this.owner = owner;
    this.fetcher = fetcher;
    this.value = Collections.emptyList();
  }

  @Override
  public int collectionSize() {
    return value.size();
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
  public synchronized Stream<V> refresh() {
    final Instant now = Instant.now();
    if (Duration.between(fetchTime, now).toMinutes() > owner.ttl()) {
      try (AutoCloseable timer = refreshLatency.start(owner.name())) {
        Stream<V> stream = fetcher.update(fetchTime);
        if (stream != null) {
          value = stream.collect(Collectors.toList());
          fetchTime = Instant.now();
          initialState = false;
          stream.close();
        }
      } catch (final Exception e) {
        e.printStackTrace();
        staleRefreshError.labels(owner.name()).inc();
      }
    }
    if (initialState) {
      throw new InitialCachePopulationException();
    }
    return value.stream();
  }
}
