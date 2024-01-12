package ca.on.oicr.gsi.shesmu.plugin.cache;

import java.time.Instant;
import java.util.List;
import java.util.Set;
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
public final class MergingRecord<V, I> extends BaseRecord<Stream<V>, List<V>> {

  /** Merge records by the supplied id */
  public static <V, I> RecordFactory<Stream<V>> by(Function<V, I> getId) {
    return fetcher -> new MergingRecord<>(fetcher, getId);
  }

  private final Function<V, I> getId;

  public MergingRecord(Updater<Stream<V>> fetcher, Function<V, I> getId) {
    super(fetcher, List.of());
    this.getId = getId;
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
    if (stream == null) {
      return null;
    }
    final List<V> buffer = stream.collect(Collectors.toList());
    stream.close();
    final Set<I> newIds = buffer.stream().map(getId).collect(Collectors.toSet());
    return Stream.concat(
            oldstate.stream().filter(item -> !newIds.contains(getId.apply(item))), buffer.stream())
        .collect(Collectors.toList());
  }

  @Override
  public Updater<?> updater() {
    return fetcher;
  }
}
