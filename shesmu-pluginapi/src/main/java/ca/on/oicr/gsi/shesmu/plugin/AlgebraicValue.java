package ca.on.oicr.gsi.shesmu.plugin;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/** A fixed-length list of heterogeneous values with type information */
public final class AlgebraicValue {
  private final Object[] elements;
  private final String name;

  /**
   * Create a new tuple from the specified array
   *
   * @param name the type tag
   * @param elements the elements in the tuple; the array must not have any references after passing
   */
  public AlgebraicValue(String name, Object... elements) {
    super();
    this.name = name;
    this.elements = elements;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    var tuple = (AlgebraicValue) o;
    return name.equals(tuple.name) && Arrays.equals(elements, tuple.elements);
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
    var result = Objects.hash(name);
    result = 31 * result + Arrays.hashCode(elements);
    return result;
  }

  /** The type tag */
  public String name() {
    return name;
  }

  @Override
  public String toString() {
    return Arrays.stream(elements)
        .map(Object::toString)
        .collect(Collectors.joining(", ", name + " [ ", "]"));
  }
}
