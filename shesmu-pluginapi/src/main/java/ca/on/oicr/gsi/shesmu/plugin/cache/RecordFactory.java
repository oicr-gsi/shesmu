package ca.on.oicr.gsi.shesmu.plugin.cache;

/**
 * Producer of new records for updater
 *
 * @param <V> the type returned by the record
 */
public interface RecordFactory<V> {

  Record<V> create(Updater<V> updater);
}
