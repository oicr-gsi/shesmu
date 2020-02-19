package ca.on.oicr.gsi.shesmu.plugin.cache;

public class InitialCachePopulationException extends RuntimeException {
  public InitialCachePopulationException(String cache) {
    super("Failed to populate cache: " + cache);
  }
}
