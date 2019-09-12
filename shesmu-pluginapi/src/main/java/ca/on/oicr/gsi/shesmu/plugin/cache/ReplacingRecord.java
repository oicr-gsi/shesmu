package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all
 */
public final class ReplacingRecord<V> extends BaseRecord<Stream<V>, List<V>> {
  private final Updater<Stream<V>> fetcher;

  public ReplacingRecord(Owner owner, Updater<Stream<V>> fetcher) {
    super(owner, Collections.emptyList());
    this.fetcher = fetcher;
  }

  @Override
  protected int collectionSize(List<V> state) {
    return state.size();
  }

  @Override
  protected Stream<V> unpack(List<V> state) {
    return state.stream();
  }

  @Override
  protected List<V> update(List<V> oldstate, Instant fetchTime) throws Exception {
    final Stream<V> stream = fetcher.update(fetchTime);
    if (stream != null) {
      final List<V> result = stream.collect(Collectors.toList());
      stream.close();
      return result;
    } else {
      return null;
    }
  }
}
