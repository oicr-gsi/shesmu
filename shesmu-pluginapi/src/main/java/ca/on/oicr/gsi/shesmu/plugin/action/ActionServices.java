package ca.on.oicr.gsi.shesmu.plugin.action;

import java.util.Set;

/** Information available to actions in order to make runtime decisions about their behaviour */
public interface ActionServices {
  /**
   * Check if any of the provide services are in an overload state
   *
   * @param services the names of the services
   */
  default Set<String> isOverloaded(String... services) {
    return isOverloaded(Set.of(services));
  }

  /**
   * Check if any of the provide services are in an overload state
   *
   * @param services the names of the services
   */
  Set<String> isOverloaded(Set<String> services);
}
