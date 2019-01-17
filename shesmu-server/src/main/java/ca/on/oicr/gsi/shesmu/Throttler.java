package ca.on.oicr.gsi.shesmu;

import java.util.Arrays;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** A method to block/rate-limit actions depending on external conditions */
public interface Throttler extends LoadedConfiguration {

  public static final ServiceLoader<Throttler> SERVICE_LOADER = ServiceLoader.load(Throttler.class);

  /**
   * Check if any throttles want to block services
   *
   * @param services the service names to check
   */
  public static boolean anyOverloaded(Set<String> services) {
    return services().anyMatch(t -> t.isOverloaded(services));
  }

  /**
   * Check if any throttles want to block services
   *
   * @param services the service names to check
   */
  public static boolean anyOverloaded(String... services) {
    return anyOverloaded(new HashSet<>(Arrays.asList(services)));
  }

  /** The throttler services that are currently loaded */
  public static Stream<Throttler> services() {
    return StreamSupport.stream(SERVICE_LOADER.spliterator(), false);
  }

  /**
   * Check throttling should be applied
   *
   * @param services a list of service names to check; this set must not be modified; these names
   *     are arbitrary and must be coordinated by {@link Action} and the throttler
   * @return true if the action should be blocked; false if it may proceed
   */
  public boolean isOverloaded(Set<String> services);
}
