package ca.on.oicr.gsi.shesmu.runtime;

import java.io.File;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.prometheus.client.Gauge;

/**
 * Utilities for making bytecode generation easier
 */
public final class RuntimeSupport {
	private static class Holder<T> {

		private final BiPredicate<T, T> equals;
		private final int hashCode;
		private final T item;

		public Holder(BiPredicate<T, T> equals, int hashCode, T item) {
			this.equals = equals;
			this.hashCode = hashCode;
			this.item = item;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Holder) {
				@SuppressWarnings("unchecked")
				final Holder<T> other = (Holder<T>) obj;
				return equals.test(other.unbox(), item);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return hashCode;
		}

		public T unbox() {
			return item;
		}

	}

	private static final Map<String, CallSite> callsites = new HashMap<>();

	public static final ObjectMapper MAPPER = new ObjectMapper();

	private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));

	public static final BinaryOperator<?> USELESS_BINARY_OPERATOR = new BinaryOperator<Object>() {

		@Override
		public Object apply(Object t, Object u) {
			throw new UnsupportedOperationException();
		}
	};

	static {
		MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

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
		return builder.append(toString(instant, format));
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
		for (int padding = width - result.length(); padding > 0; padding--) {
			builder.append("0");
		}
		return builder.append(result);
	}

	public static Optional<Stream<Path>> dataPaths() {
		return environmentVariable().map(RuntimeSupport::parsePaths);
	}

	/**
	 * Determine the difference between two instants, in seconds.
	 */
	@RuntimeInterop
	public static long difference(Instant left, Instant right) {
		return Duration.between(right, left).getSeconds();
	}

	public static Optional<String> environmentVariable() {
		return Optional.ofNullable(System.getenv("SHESMU_DATA"));
	}

	/**
	 * Left join a stream of input against another input format
	 *
	 * @param input
	 *            the stream to be joined against
	 * @param inner
	 *            the type of the inner (right) input stream
	 * @param inputLoader
	 *            a function to load this input format
	 * @param joiner
	 *            a function to create an intermediate joined type from the two
	 *            types
	 * @param makeKey
	 *            a function to create a new output type; it must accept a joined
	 *            type where the right side will be null
	 * @param collector
	 *            a function that processes joined inputs with both right and left
	 *            values to an output
	 * @return
	 */
	@RuntimeInterop
	public static <I, N, J, O> Stream<O> leftJoin(Stream<I> input, Class<N> inner,
			Function<Class<N>, Stream<N>> inputLoader, BiFunction<I, N, J> joiner, Function<J, O> makeKey,
			BiConsumer<O, J> collector) {
		return input.map(left -> {
			final O output = makeKey.apply(joiner.apply(left, null));
			inputLoader.apply(inner).forEach(right -> collector.accept(output, joiner.apply(left, right)));
			return output;
		});
	}

	public static <T> Optional<T> merge(Optional<T> left, Optional<T> right,
			BiFunction<? super T, ? super T, ? extends T> merge) {
		if (left.isPresent() && right.isPresent()) {
			return Optional.of(merge.apply(left.get(), right.get()));
		}
		if (left.isPresent()) {
			return left;
		}
		return right;
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

	public static Stream<Path> parsePaths(String pathVariable) {
		return PATH_SEPARATOR.splitAsStream(pathVariable).map(Paths::get);
	}

	/**
	 * Pick the first value for sorted groups of items.
	 *
	 * @param input
	 *            the stream of input items
	 * @param hashCode
	 *            the hashing for the grouping of interest over the input type
	 * @param equals
	 *            a equality for the grouping of interest over the input type
	 * @param comparator
	 *            the sorting operating to be performed on the grouped input
	 */
	@RuntimeInterop
	public static <T> Stream<T> pick(Stream<T> input, ToIntFunction<T> hashCode, BiPredicate<T, T> equals,
			Comparator<T> comparator) {
		final Map<Holder<T>, List<T>> groups = input
				.collect(Collectors.groupingBy(item -> new Holder<>(equals, hashCode.applyAsInt(item), item)));
		input.close();
		return groups.values().stream().map(list -> list.stream().sorted(comparator).findFirst().get());
	}

	public static String printHexBinary(byte[] values) {
		final StringBuilder builder = new StringBuilder(values.length * 2);
		for (final byte b : values) {
			builder.append(String.format("%02X", b));
		}
		return builder.toString();
	}

	/**
	 * This is a boot-strap method for <tt>INVOKE DYNAMIC</tt> to match a regular
	 * expression (which is the method name). s
	 */
	@RuntimeInterop
	public static CallSite regexBootstrap(Lookup lookup, String signature, MethodType type, String regex)
			throws NoSuchMethodException, IllegalAccessException {
		if (!type.returnType().equals(Pattern.class)) {
			throw new IllegalArgumentException("Method cannot return non-Pattern type.");
		}
		if (type.parameterCount() != 0) {
			throw new IllegalArgumentException("Method must take exactly no arguments.");
		}
		if (callsites.containsKey(regex)) {
			return callsites.get(regex);
		}
		final Pattern pattern = Pattern.compile(regex);
		final CallSite callsite = new ConstantCallSite(MethodHandles.constant(Pattern.class, pattern));
		callsites.put(regex, callsite);
		return callsite;
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
		input.close();
		return groups.entrySet().stream().peek(e -> e.getValue().stream().forEach(x -> collector.accept(e.getKey(), x)))
				.map(Entry::getKey);
	}

	/**
	 * Clip the extension off a file path and return just the filename
	 */
	public static String removeExtension(Path fileName, String extension) {
		final String fileNamePart = fileName.getFileName().toString();
		return fileNamePart.substring(0, fileNamePart.length() - extension.length());
	}

	public static <T> Stream<T> reverse(Stream<T> input) {
		final List<T> data = input.collect(Collectors.toList());
		Collections.reverse(data);
		return data.stream();
	}

	/**
	 * Stream an iterable object
	 */
	public static <T> Stream<T> stream(Iterable<T> iterable) {
		return stream(iterable.spliterator());
	}

	/**
	 * Convert an iterator to a stream
	 */
	public static <T> Stream<T> stream(Iterator<T> iterator) {
		return stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED));
	}

	public static <T> Stream<T> stream(Spliterator<T> spliterator) {
		return StreamSupport.stream(spliterator, false);
	}

	@RuntimeInterop
	public static String toString(Instant instant, String format) {
		return DateTimeFormatter.ofPattern(format).format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
	}

	private RuntimeSupport() {
	}
}
