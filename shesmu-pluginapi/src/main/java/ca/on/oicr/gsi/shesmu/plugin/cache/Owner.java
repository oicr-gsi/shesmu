package ca.on.oicr.gsi.shesmu.plugin.cache;

/** Interface for caches so that records can communicate with their containers */
public interface Owner {
  /** The name of the cache for use in monitoring */
  String name();

  /** The time-to-live for a record in cache */
  long ttl();
}
