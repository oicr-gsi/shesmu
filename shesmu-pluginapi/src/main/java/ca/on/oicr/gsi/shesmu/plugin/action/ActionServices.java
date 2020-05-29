package ca.on.oicr.gsi.shesmu.plugin.action;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Information available to actions in order to make runtime decisions about their behaviour */
public interface ActionServices {
  /**
   * Check if any of the provide services are in an overload state
   *
   * @param services the names of the services
   */
  default Set<String> isOverloaded(String... services) {
    return isOverloaded(new TreeSet<>(Arrays.asList(services)));
  }

  /**
   * Check if any of the provide services are in an overload state
   *
   * @param services the names of the services
   */
  Set<String> isOverloaded(Set<String> services);
}
