package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.Optional;

/**
 * Hold on to a single value
 *
 * <p>If the value could not be fetched, the previous value will be used instead.
 */
public final class SimpleRecord<V> extends BaseRecord<Optional<V>, Optional<V>> {

  public SimpleRecord(Updater<Optional<V>> fetcher) {
    super(fetcher, Optional.empty());
  }

  @Override
  protected int collectionSize(Optional<V> value) {
    return value.isPresent() ? 1 : 0;
  }

  @Override
  protected Optional<V> unpack(Optional<V> state) {
    return state;
  }

  @Override
  protected Optional<V> update(Optional<V> oldstate, Instant fetchTime) throws Exception {
    final var buffer = fetcher.update(fetchTime);
    return buffer.isPresent() ? buffer : null;
  }

  @Override
  public Updater<?> updater() {
    return fetcher;
  }
}
