package ca.on.oicr.gsi.shesmu.plugin.cache;

import ca.on.oicr.gsi.shesmu.plugin.ErrorableStream;
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
    // The stream must be closed even when collecting it fails, since it may be holding an HTTP
    // connection open
    try (final ErrorableStream<V> stream = new ErrorableStream<>(fetcher.update(fetchTime))) {
      return stream.isOk() ? stream.collect(Collectors.toList()) : null;
    }
  }

  @Override
  public Updater<?> updater() {
    return fetcher;
  }
}
