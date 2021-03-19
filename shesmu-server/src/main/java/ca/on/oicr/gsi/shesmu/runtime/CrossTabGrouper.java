package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CrossTabGrouper<I, T, O> implements Grouper<I, O> {
  private final Function<I, T> partition;
  private final Supplier<BiConsumer<O, I>> collectorSupplier;

  public CrossTabGrouper(Function<I, T> partition, Supplier<BiConsumer<O, I>> collectorSupplier) {
    this.partition = partition;
    this.collectorSupplier = collectorSupplier;
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    // Split all the input up by partition
    final var partitions =
        inputs.stream().collect(Collectors.groupingBy(partition, Collectors.toList()));

    // Create a stream of things that create a new subgroup
    Stream<Supplier<Subgroup<I, O>>> stream =
        Stream.of(() -> new Subgroup<>(collectorSupplier.get()));
    // For each partition, take all the previously produced subgroups and cross them with each input
    // item
    for (final var partition : partitions.values()) {
      stream =
          stream.flatMap(
              supplier ->
                  partition.stream()
                      .map(
                          item ->
                              () -> {
                                final var subgroup = supplier.get();
                                subgroup.add(item);
                                return subgroup;
                              }));
    }
    // Now, produce all those aggregate groups
    return stream.map(Supplier::get);
  }
}
