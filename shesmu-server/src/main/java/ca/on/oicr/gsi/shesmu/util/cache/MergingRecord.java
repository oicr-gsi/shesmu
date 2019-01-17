package ca.on.oicr.gsi.shesmu.util.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores an incrementally appending list of items. When new items are provided, old ones with
 * matching IDs are replaced
 *
 * @param <V> the type of the items
 * @param <I> the type of the ids
 */
public final class MergingRecord<V, I> implements Record<Stream<V>> {

  /** Merge records by the supplied id */
  public static <V, I> BiFunction<Owner, Updater<Stream<V>>, Record<Stream<V>>> by(
      Function<V, I> getId) {
    return (owner, fetcher) -> new MergingRecord<>(owner, fetcher, getId);
  }

  private final Updater<Stream<V>> fetcher;
  private Instant fetchTime = Instant.EPOCH;
  private final Function<V, I> getId;
  private final Owner owner;

  private List<V> value;

  public MergingRecord(Owner owner, Updater<Stream<V>> fetcher, Function<V, I> getId) {
    this.owner = owner;
    this.fetcher = fetcher;
    this.getId = getId;
    List<V> value;
    try {
      value =
          Optional.of(fetcher.update(fetchTime))
              .orElse(Stream.empty())
              .collect(Collectors.toList());
      fetchTime = Instant.now();
    } catch (final Exception e) {
      e.printStackTrace();
      value = Collections.emptyList();
    }
    this.value = value;
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
        final List<V> buffer =
            Optional.ofNullable(fetcher.update(fetchTime))
                .orElse(Stream.empty())
                .collect(Collectors.toList());
        final Set<I> newIds = buffer.stream().map(getId).collect(Collectors.toSet());
        value =
            Stream.concat(
                    value.stream().filter(item -> !newIds.contains(getId.apply(item))),
                    buffer.stream())
                .collect(Collectors.toList());

        fetchTime = Instant.now();
      } catch (final Exception e) {
        e.printStackTrace();
        staleRefreshError.labels(owner.name()).inc();
      }
    }
    return value.stream();
  }

  @Override
  public int collectionSize() {
    return value.size();
  }
}
