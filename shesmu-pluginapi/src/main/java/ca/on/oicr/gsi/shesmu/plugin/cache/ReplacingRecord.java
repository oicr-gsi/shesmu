package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Takes a stream of items and stores them. When updated, it discards the existing items and
 * replaces them all
 */
public final class ReplacingRecord<V> extends BaseRecord<Stream<V>, List<V>> {

  public ReplacingRecord(Updater<Stream<V>> fetcher) {
    super(fetcher, List.of());
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
    final var stream = fetcher.update(fetchTime);
    if (stream != null) {
      final var result = stream.collect(Collectors.toList());
      stream.close();
      return result;
    } else {
      return null;
    }
  }

  @Override
  public Updater<?> updater() {
    return fetcher;
  }
}
