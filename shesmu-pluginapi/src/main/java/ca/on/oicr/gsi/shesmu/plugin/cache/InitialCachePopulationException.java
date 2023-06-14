package ca.on.oicr.gsi.shesmu.plugin.cache;

public class InitialCachePopulationException extends RuntimeException {
  public InitialCachePopulationException(String cache, String message) {
    super("Failed to populate cache: " + cache + " because: " + message);
  }
}
