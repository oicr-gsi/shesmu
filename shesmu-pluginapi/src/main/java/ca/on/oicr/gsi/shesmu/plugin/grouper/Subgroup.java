package ca.on.oicr.gsi.shesmu.plugin.grouper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Describe a subgroup created by a {@link Grouper}
 *
 * @param <I> the input row type; provided by Shesmu
 * @param <O> the output row type; provided by Shesmu
 */
public final class Subgroup<I, O> {
  private final BiConsumer<O, I> collector;
  private final List<I> items = new ArrayList<>();

  /**
   * Create a new empty subgroup
   *
   * @param collector the callback to collect all the values in this grouping operation
   */
  public Subgroup(BiConsumer<O, I> collector) {
    this.collector = collector;
  }

  /**
   * Create a new populated subgroup
   *
   * @param collector the callback to collect all the values in this grouping operation
   * @param items the input rows to add to the group
   */
  public Subgroup(BiConsumer<O, I> collector, Stream<I> items) {
    this(collector);
    addAll(items);
  }

  /** Add a single input row */
  public void add(I item) {
    items.add(item);
  }
  /** Add a collection of input rows */
  public void addAll(Collection<I> items) {
    this.items.addAll(items);
  }
  /** Add a stream of input rows */
  public void addAll(Stream<I> items) {
    items.forEach(this.items::add);
  }

  /** Create the output value for this input row */
  public O build(Function<I, O> makeKey) {
    final O output = makeKey.apply(items.get(0));
    for (final I item : items) {
      collector.accept(output, item);
    }
    return output;
  }

  /** Check if this subgroup is valid */
  public boolean valid() {
    return !items.isEmpty();
  }
}
