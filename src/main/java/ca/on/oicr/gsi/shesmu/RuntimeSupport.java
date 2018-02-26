package ca.on.oicr.gsi.shesmu;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.prometheus.client.Gauge;

/**
 * Utilities for making bytecode generation easier
 */
public final class RuntimeSupport {

	public static final ObjectMapper MAPPER = new ObjectMapper();

	public static final BinaryOperator<?> USELESS_BINARY_OPERATOR = new BinaryOperator<Object>() {

		@Override
		public Object apply(Object t, Object u) {
			throw new UnsupportedOperationException();
		}
	};

	/**
	 * Put a formatted date-time into a string builder
	 *
	 * @param builder
	 *            the string builder to append to
	 * @param instant
	 *            the instant to use
	 * @param format
	 *            the format code for {@link DateTimeFormatter}
	 */
	@RuntimeInterop
	public static StringBuilder appendFormatted(StringBuilder builder, Instant instant, String format) {
		return builder
				.append(DateTimeFormatter.ofPattern(format).format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC)));
	}

	/**
	 * Write a zero-padded number to a string builder
	 *
	 * @param builder
	 *            the string builder to append to
	 * @param value
	 *            the number of append
	 * @param width
	 *            the number of digits the number should be
	 */
	@RuntimeInterop
	public static StringBuilder appendFormatted(StringBuilder builder, long value, int width) {
		final String result = Long.toString(value);
		for (int padding = width = result.length(); padding > 0; padding--) {
			builder.append("0");
		}
		return builder.append(result);
	}

	/**
	 * Determine the difference between two instants, in seconds.
	 */
	@RuntimeInterop
	public static long difference(Instant left, Instant right) {
		return Duration.between(right, left).getSeconds();
	}

	/**
	 * Add Prometheus monitoring to a stream.
	 *
	 * @param input
	 *            the stream to monitor
	 * @param gauge
	 *            the gauge to write the output to
	 * @param computeValues
	 *            a function to compute the values of the labels for the gauge; the
	 *            order is preserved
	 */
	@RuntimeInterop
	public static <T> Stream<T> monitor(Stream<T> input, Gauge gauge, Function<T, String[]> computeValues) {
		return input.peek(item -> gauge.labels(computeValues.apply(item)).inc());
	}

	/**
	 * Group a stream of input and output the grouped output stream.
	 *
	 * @param <O>
	 *            the output type of the grouping. It must have the following
	 *            behaviour:
	 *            <ul>
	 *            <li>they must have a single argument constructor that selects all
	 *            the group-by elements out of the input type
	 *            <li>the must have additional fields to collect the “grouped”
	 *            elements into collections
	 *            <li>the must have a an {{@link #equals(Object)} and
	 *            {@link #hashCode()} methods that return true if the group-by
	 *            fields are identical but ignore the grouped collections
	 *            </ul>
	 *
	 * @param input
	 *            the stream to consume
	 * @param makeKey
	 *            the constructor that makes an output item for an input item
	 * @param collector
	 *            a function that adds all of the “collected” input data to an
	 *            output value
	 * @return the grouped output stream
	 */
	@RuntimeInterop
	public static <I, O> Stream<O> regroup(Stream<I> input, Function<I, O> makeKey, BiConsumer<O, I> collector) {
		final Map<O, List<I>> groups = input.collect(Collectors.groupingBy(makeKey));
		return groups.entrySet().stream().peek(e -> e.getValue().stream().forEach(x -> collector.accept(e.getKey(), x)))
				.map(Entry::getKey);
	}

	private RuntimeSupport() {
	}
}
