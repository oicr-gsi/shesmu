package ca.on.oicr.gsi.shesmu.util;

import io.prometheus.client.Gauge;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Packs named objects into a map for retrieval
 *
 * @param <T> the values in the map
 */
public final class NameLoader<T> {

  private final Map<String, T> items;
  private static final Gauge duplicateNames =
      Gauge.build("shesmu_name_conflict", "The number of names loaded in conflict.").register();

  /**
   * Create a new map from the provided objects
   *
   * @param data the stream of objects
   * @param getName a function to extract a name from each
   */
  public NameLoader(Stream<? extends T> data, Function<T, String> getName) {
    duplicateNames.set(0);
    items =
        data.collect(
            Collectors.toMap(
                getName,
                Function.identity(),
                (a, b) -> {
                  duplicateNames.inc();
                  System.err.printf(
                      "Duplicated names for %s. Randomly picking one of: %s %s\n",
                      getName.apply(a), a, b);
                  return a;
                }));
  }

  /** Stream all the objects in the map */
  public Stream<T> all() {
    return items.values().stream();
  }

  /** Get a particular object by name */
  public T get(String name) {
    return items.get(name);
  }
}
