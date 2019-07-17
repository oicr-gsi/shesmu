package ca.on.oicr.gsi.shesmu.plugin;

import java.util.stream.Stream;

/** Allows finding out which services are required by exported functions and constants */
public interface RequiredServices {
  /**
   * Stop olives that use functions and constants when a service is throttled.
   *
   * <p>This does not affect actions!
   *
   * @return the services names needed
   */
  default Stream<String> services() {
    return Stream.empty();
  }
}
