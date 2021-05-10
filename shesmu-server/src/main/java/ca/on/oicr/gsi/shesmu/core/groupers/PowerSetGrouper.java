package ca.on.oicr.gsi.shesmu.core.groupers;

import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class PowerSetGrouper<I, O> implements Grouper<I, O> {

  private final Supplier<BiConsumer<O, I>> collector;

  public PowerSetGrouper(Supplier<BiConsumer<O, I>> collector) {
    this.collector = collector;
  }

  private IntStream bitsSet(long bits, int size) {
    return IntStream.range(0, size).filter(index -> (bits & (1L << index)) != 0);
  }

  @Override
  public Stream<Subgroup<I, O>> group(List<I> inputs) {
    return LongStream.range(1, (1L << inputs.size()) - 1)
        .mapToObj(
            bits ->
                new Subgroup<>(
                    collector.get(), bitsSet(bits, inputs.size()).mapToObj(inputs::get)));
  }
}
