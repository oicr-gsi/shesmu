package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A fixed-length list of heterogeneous values
 *
 * <p>This class is mostly a thin wrapper on an array of objects with sensible equals/hashcode
 * methods
 */
public final class Tuple {
  private final Object[] elements;

  /**
   * Create a new tuple from the specified array
   *
   * @param elements the elements in the tuple; the array must not have any references after passing
   *     to the constructors
   */
  public Tuple(Object... elements) {
    super();
    this.elements = elements;
  }

  /**
   * Create a new tuple containing the all the elements from this tuple followed by all the elements
   * of the supplied tuple.
   */
  public Tuple concat(Tuple other) {
    final var concat = Arrays.copyOf(elements, elements.length + other.elements.length);
    System.arraycopy(other.elements, 0, concat, elements.length, other.elements.length);
    return new Tuple(concat);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final var other = (Tuple) obj;
    return Arrays.equals(elements, other.elements);
  }

  /**
   * Get an element from the tuple
   *
   * @param index the zero-based position in the tuple
   */
  public Object get(int index) {
    return elements[index];
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + Arrays.hashCode(elements);
    return result;
  }

  @SuppressWarnings("unchecked")
  public Tuple order(Imyhat type) {
    final var array = Arrays.copyOf(elements, elements.length);
    Arrays.sort(array, (Comparator<Object>) type.comparator());
    return new Tuple(array);
  }

  /** Get all the elements in the tuple */
  public Stream<Object> stream() {
    return Arrays.stream(elements);
  }

  @Override
  public String toString() {
    return Arrays.stream(elements)
        .map(Object::toString)
        .collect(Collectors.joining(", ", "[ ", "]"));
  }
}
