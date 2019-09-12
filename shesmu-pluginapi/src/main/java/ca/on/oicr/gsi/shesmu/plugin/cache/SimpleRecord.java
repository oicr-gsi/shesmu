package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.Optional;

/**
 * Hold on to a single value
 *
 * <p>If the value could not be fetched, the previous value will be used instead.
 */
public final class SimpleRecord<V> extends BaseRecord<Optional<V>, Optional<V>> {

  private final Updater<Optional<V>> fetcher;

  public SimpleRecord(Owner owner, Updater<Optional<V>> fetcher) {
    super(owner, Optional.empty());
    this.fetcher = fetcher;
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
    final Optional<V> buffer = fetcher.update(fetchTime);
    return buffer.isPresent() ? buffer : null;
  }
}
