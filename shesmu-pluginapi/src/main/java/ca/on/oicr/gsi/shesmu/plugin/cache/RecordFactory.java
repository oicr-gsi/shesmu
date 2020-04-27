package ca.on.oicr.gsi.shesmu.plugin.cache;

/**
 * Producer of new records for updater
 *
 * @param <I> the type returned by the updater
 * @param <V> the type returned by the record
 */
public interface RecordFactory<I, V> {

  Record<V> create(Updater<I> updater);
}
