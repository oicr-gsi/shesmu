package ca.on.oicr.gsi.shesmu.plugin.grouper;

import java.util.List;
import java.util.stream.Stream;

/**
 * Perform a complex sub grouping operation
 *
 * @param <I> the input record type
 * @param <O> the output record type
 */
public interface Grouper<I, O> {
  /**
   * Group the inputs provided into sensible subgroups. The grouper may drop input or assign the
   * same input to multiple groups; it should not include the same input in the same group multiple
   * times
   *
   * @param inputs the inputs to be grouped; the list is now owned by the grouper and the grouper
   *     can change it
   * @return the groups produced. For each output group, the collection function is provided. This
   *     is a (possibly modified) operation that collects input data into the output. The grouper
   *     will receive a template for this operation at construction.
   */
  Stream<Subgroup<I, O>> group(List<I> inputs);
}
