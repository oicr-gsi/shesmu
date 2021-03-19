package ca.on.oicr.gsi.shesmu.runtime;

import java.util.HashSet;
import java.util.Set;

public class UnivaluedGroupAccumulator<T> {
  private final T defaultValue;
  private final Set<T> items = new HashSet<>();

  public UnivaluedGroupAccumulator(T defaultValue) {

    this.defaultValue = defaultValue;
  }

  public void add(T value) {
    items.add(value);
  }

  public T get() {
    return items.size() == 1 ? items.iterator().next() : defaultValue;
  }
}
