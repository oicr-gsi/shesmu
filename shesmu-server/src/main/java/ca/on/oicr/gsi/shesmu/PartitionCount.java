package ca.on.oicr.gsi.shesmu;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class PartitionCount {

	public static final Collector<Boolean, PartitionCount, Tuple> COLLECTOR = new Collector<Boolean, PartitionCount, Tuple>() {

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

	private long falseCount;
	private long trueCount;

	public void accumulate(boolean value) {
		if (value) {
			trueCount++;
		} else {
			falseCount++;
		}
	}

	public PartitionCount combine(PartitionCount other) {
		trueCount += other.trueCount;
		falseCount += other.falseCount;
		return this;
	}

	public Tuple toTuple() {
		return new Tuple(trueCount, falseCount);
	}
}
