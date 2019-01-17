package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Imyhat;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class PartitionCount {
  @RuntimeInterop
  public static final Collector<Boolean, PartitionCount, Tuple> COLLECTOR =
      new Collector<Boolean, PartitionCount, Tuple>() {

        @Override
        public BiConsumer<PartitionCount, Boolean> accumulator() {
          return PartitionCount::accumulate;
        }

        @Override
        public Set<Characteristics> characteristics() {
          return EnumSet.of(Characteristics.UNORDERED);
        }

        @Override
        public BinaryOperator<PartitionCount> combiner() {
          return PartitionCount::combine;
        }

        @Override
        public Function<PartitionCount, Tuple> finisher() {
          return PartitionCount::toTuple;
        }

        @Override
        public Supplier<PartitionCount> supplier() {
          return PartitionCount::new;
        }
      };

  public static final Imyhat TYPE =
      new Imyhat.ObjectImyhat(
          Stream.of(
              new Pair<>("matched_count", Imyhat.INTEGER),
              new Pair<>("not_matched_count", Imyhat.INTEGER)));

  private long matchedCount;
  private long notMatchedCount;

  @RuntimeInterop
  public void accumulate(boolean value) {
    if (value) {
      matchedCount++;
    } else {
      notMatchedCount++;
    }
  }

  public PartitionCount combine(PartitionCount other) {
    matchedCount += other.matchedCount;
    notMatchedCount += other.notMatchedCount;
    return this;
  }

  @RuntimeInterop
  public Tuple toTuple() {
    return new Tuple(matchedCount, notMatchedCount);
  }
}
