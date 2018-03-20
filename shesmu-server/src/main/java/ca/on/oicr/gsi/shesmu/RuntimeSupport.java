package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.prometheus.client.Gauge;

/**
 * Utilities for making bytecode generation easier
 */
public final class RuntimeSupport {

	private static final Map<String, CallSite> callsites = new HashMap<>();

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

	public static Optional<Path> dataDirectory() {
		return dataDirectory(environmentVariable());
	}

	public static Optional<Path> dataDirectory(Optional<String> root) {
		return root//
				.map(Paths::get)//
				.filter(d -> Files.isDirectory(d) && Files.isReadable(d));
	}

	public static <T> Optional<T> dataFile(Class<T> clazz, String name) {
		return dataFile(dataDirectory(), name).flatMap(path -> {
			try {
				return Optional.of(RuntimeSupport.MAPPER.readValue(Files.readAllBytes(path), clazz));
			} catch (final IOException e) {
				e.printStackTrace();
				return Optional.empty();
			}
		});
	}

	public static Optional<Path> dataFile(Optional<Path> dir, String name) {
		return dir//
				.map(root -> root.resolve(name))//
				.filter(Files::isReadable);
	}

	public static Optional<Path> dataFile(String name) {
		return dataFile(dataDirectory(), name);
	}

	public static <T> Stream<T> dataFiles(Class<T> clazz, String suffix) {
		return dataFiles(dataDirectory(), clazz, suffix);
	}

	public static <T> Stream<T> dataFiles(Optional<Path> root, Class<T> clazz, String suffix) {
		return dataFiles(root, suffix)//
				.<T>map(file -> {
					try {
						return RuntimeSupport.MAPPER.readValue(Files.readAllBytes(file), clazz);
					} catch (final IOException e) {
						e.printStackTrace();
						return null;
					}
				})//
				.filter(Objects::nonNull);
	}

	public static Stream<Path> dataFiles(Optional<Path> root, String suffix) {
		return root//
				.<Stream<Path>>map(path -> {
					try (Stream<Path> children = Files.walk(path, 1)) {
						return children//
								.filter(Files::isRegularFile)//
								.filter(f -> f.getFileName().toString().toLowerCase().endsWith(suffix))//
								.collect(Collectors.toList()).stream();
					} catch (final IOException e) {
						e.printStackTrace();
						return Stream.empty();
					}
				}).orElseGet(Stream::empty);
	}

	public static Stream<Path> dataFiles(String extension) {
		return dataFiles(dataDirectory(), extension);
	}

	public static <T> Stream<T> dataFilesForPath(Optional<String> root, Class<T> clazz, String suffix) {
		return dataFiles(dataDirectory(root), clazz, suffix);
	}

	public static Stream<Path> dataFilesForPath(Optional<String> root, String suffix) {
		return dataFiles(dataDirectory(root), suffix);
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

	@RuntimeInterop
	public static CallSite regexBootstrap(Lookup lookup, String signature, MethodType type)
			throws NoSuchMethodException, IllegalAccessException {
		if (!type.returnType().equals(boolean.class)) {
			throw new IllegalArgumentException("Method cannot return non-boolean type.");
		}
		if (type.parameterCount() != 1 || !type.parameterType(0).equals(CharSequence.class)) {
			throw new IllegalArgumentException("Method must take exactly 1 character sequence parameter.");
		}
		if (callsites.containsKey(signature)) {
			return callsites.get(signature);
		}
		final Pattern pattern = Pattern.compile(signature);
		pattern.matcher("").matches();
		final MethodHandle matcher = lookup.findVirtual(Pattern.class, "matcher",
				MethodType.methodType(Matcher.class, CharSequence.class));
		final MethodHandle matches = lookup.findVirtual(Matcher.class, "matches", MethodType.methodType(boolean.class));
		final CallSite callsite = new ConstantCallSite(
				MethodHandles.filterReturnValue(MethodHandles.insertArguments(matcher, 0, pattern), matches));
		callsites.put(signature, callsite);
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
		return groups.entrySet().stream().peek(e -> e.getValue().stream().forEach(x -> collector.accept(e.getKey(), x)))
				.map(Entry::getKey);
	}

	private RuntimeSupport() {
	}
}
