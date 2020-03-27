package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Grouper;
import ca.on.oicr.gsi.shesmu.plugin.grouper.Subgroup;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonArray;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.prometheus.client.Gauge;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utilities for making bytecode generation easier */
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

  /** Create a copy of a set with an additional item. */
  @RuntimeInterop
  public static <T> Set<T> addItem(Imyhat type, Set<T> left, T right) {
    Set<T> result = type.newSet();
    result.addAll(left);
    result.add(right);
    return result;
  }

  /**
   * Put a formatted date-time into a string builder
   *
   * @param builder the string builder to append to
   * @param instant the instant to use
   * @param format the format code for {@link DateTimeFormatter}
   */
  @RuntimeInterop
  public static StringBuilder appendFormatted(
      StringBuilder builder, Instant instant, String format) {
    return builder.append(toString(instant, format));
  }

  /**
   * Write a zero-padded number to a string builder
   *
   * @param builder the string builder to append to
   * @param value the number of append
   * @param width the number of digits the number should be
   */
  @RuntimeInterop
  public static StringBuilder appendFormatted(StringBuilder builder, long value, int width) {
    final String result = Long.toString(value);
    for (int padding = width - result.length(); padding > 0; padding--) {
      builder.append("0");
    }
    return builder.append(result);
  }

  /** Determine the difference between two instants, in seconds. */
  @RuntimeInterop
  public static long difference(Instant left, Instant right) {
    return Duration.between(right, left).getSeconds();
  }

  /** Determine the difference between two sets. */
  @RuntimeInterop
  public static <T> Set<T> difference(Imyhat type, Set<T> left, Set<T> right) {
    Set<T> result = type.newSet();
    result.addAll(left);
    result.removeAll(right);
    return result;
  }

  @RuntimeInterop
  public static <I, T, O> Stream<O> flatten(
      Stream<I> input, Function<I, Set<T>> explode, BiFunction<I, T, O> make) {
    return input.flatMap(i -> explode.apply(i).stream().map(v -> make.apply(i, v)));
  }

  public static JsonNode getJson(JsonNode node, String name) {
    final JsonNode result = node.get(name);
    return result == null ? JsonNodeFactory.instance.nullNode() : result;
  }

  @RuntimeInterop
  public static <I, N, K, O> Stream<O> join(
      Stream<I> input,
      Stream<N> inner,
      Function<I, K> makeOuterKey,
      Function<N, K> makeInnerKey,
      BiFunction<I, N, O> joiner) {
    final Map<K, List<I>> inputGroups = input.collect(Collectors.groupingBy(makeOuterKey));
    input.close();
    final Map<K, List<N>> innerGroups = inner.collect(Collectors.groupingBy(makeInnerKey));
    inner.close();
    return inputGroups
        .entrySet()
        .stream()
        .flatMap(
            e ->
                innerGroups
                    .getOrDefault(e.getKey(), Collections.emptyList())
                    .stream()
                    .flatMap(n -> e.getValue().stream().map(i -> joiner.apply(i, n))));
  }

  @RuntimeInterop
  public static Stream<JsonNode> jsonElements(JsonNode node) {
    return Utils.stream(node.elements());
  }

  @RuntimeInterop
  public static Stream<Tuple> jsonFields(JsonNode node) {
    return Utils.stream(node.fields()).map(e -> new Tuple(e.getKey(), e.getValue()));
  }

  @RuntimeInterop
  public static JsonNode jsonMap(Map<String, JsonNode> map) {
    final ArrayNode array = MAPPER.createArrayNode();
    for (final Map.Entry<String, JsonNode> entry : map.entrySet()) {
      final ArrayNode row = array.addArray();
      row.add(entry.getKey());
      row.add(entry.getValue());
    }
    return array;
  }

  /**
   * Left join a stream of input against another input format
   *
   * @param input the stream to be joined against
   * @param inner the type of the inner (right) input stream
   * @param joiner a function to create an intermediate joined type from the two types
   * @param makeOuterKey create the joining key from an outer record
   * @param makeInnerKey create the joining key from an inner record
   * @param makeOutput a function to create a new output type; it must accept a joined type where
   *     the right side will be null
   * @param collector a function that processes joined inputs with both right and left values to an
   *     output
   * @return
   */
  @RuntimeInterop
  public static <I, N, K, J, O> Stream<O> leftJoin(
      Stream<I> input,
      Stream<N> inner,
      Function<I, K> makeOuterKey,
      Function<N, K> makeInnerKey,
      BiFunction<I, N, J> joiner,
      Function<J, O> makeOutput,
      BiConsumer<O, J> collector) {
    final Map<K, List<I>> inputGroups = input.collect(Collectors.groupingBy(makeOuterKey));
    input.close();
    final Map<K, List<N>> innerGroups = inner.collect(Collectors.groupingBy(makeInnerKey));

    return inputGroups
        .entrySet()
        .stream()
        .flatMap(
            e -> {
              final List<N> innerData =
                  innerGroups.getOrDefault(e.getKey(), Collections.emptyList());

              return e.getValue()
                  .stream()
                  .map(
                      left -> {
                        final O output = makeOutput.apply(joiner.apply(left, null));
                        innerData.forEach(
                            right -> collector.accept(output, joiner.apply(left, right)));
                        return output;
                      });
            });
  }

  @RuntimeInterop
  public static <T> Optional<T> merge(Optional<T> left, Supplier<Optional<T>> right) {
    return left.isPresent() ? left : right.get();
  }

  /**
   * Add Prometheus monitoring to a stream.
   *
   * @param input the stream to monitor
   * @param gauge the gauge to write the output to
   * @param computeValues a function to compute the values of the labels for the gauge; the order is
   *     preserved
   */
  @RuntimeInterop
  public static <T> Stream<T> monitor(
      Stream<T> input, Gauge gauge, Function<T, String[]> computeValues) {
    return input.peek(item -> gauge.labels(computeValues.apply(item)).inc());
  }

  public static void packJson(Imyhat type, ObjectNode node, String name, Object value) {
    type.accept(new PackJsonObject(node, name), value);
  }

  static void packJson(Imyhat type, ArrayNode node, Object value) {
    type.accept(new PackJsonArray(node), value);
  }

  @RuntimeInterop
  public static Optional<Double> parseDouble(String input) {
    try {
      return Optional.of(Double.parseDouble(input));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  @RuntimeInterop
  public static Optional<Long> parseLong(String input) {
    try {
      return Optional.of(Long.parseLong(input));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /**
   * Pick the first value for sorted groups of items.
   *
   * @param input the stream of input items
   * @param hashCode the hashing for the grouping of interest over the input type
   * @param equals a equality for the grouping of interest over the input type
   * @param comparator the sorting operating to be performed on the grouped input
   */
  @RuntimeInterop
  public static <T> Stream<T> pick(
      Stream<T> input,
      ToIntFunction<T> hashCode,
      BiPredicate<T, T> equals,
      Comparator<T> comparator) {
    final Map<Holder<T>, List<T>> groups =
        input.collect(
            Collectors.groupingBy(item -> new Holder<>(equals, hashCode.applyAsInt(item), item)));
    input.close();
    return groups.values().stream().map(list -> list.stream().min(comparator).get());
  }

  /**
   * This is a boot-strap method for <tt>INVOKE DYNAMIC</tt> to match a regular expression (which is
   * the method name). s
   */
  @RuntimeInterop
  public static CallSite regexBootstrap(
      Lookup lookup, String signature, MethodType type, String regex)
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
   * Group items in to outer groups, then apply a complex subgrouping operation to produce subgroups
   * from this input
   *
   * @param input the stream of input items
   * @param grouper a way to create subgroups for each outer group.
   * @param makeKey a value for a key that is common across the bulk groups
   */
  @RuntimeInterop
  public static <I, O> Stream<O> regroup(
      Stream<I> input, Grouper<I, O> grouper, Function<I, O> makeKey) {
    final Map<O, List<I>> groups = input.collect(Collectors.groupingBy(makeKey));
    input.close();
    return groups
        .values()
        .stream()
        .flatMap(
            list ->
                grouper
                    .group(list)
                    .filter(Subgroup::valid)
                    .map(subgroup -> subgroup.build(makeKey)));
  }

  /**
   * Group a stream of input and output the grouped output stream.
   *
   * @param <O> the output type of the grouping. It must have the following behaviour:
   *     <ul>
   *       <li>they must have a single argument constructor that selects all the group-by elements
   *           out of the input type
   *       <li>the must have additional fields to collect the “grouped” elements into collections
   *       <li>the must have a an {{@link #equals(Object)} and {@link #hashCode()} methods that
   *           return true if the group-by fields are identical but ignore the grouped collections
   *     </ul>
   *
   * @param input the stream to consume
   * @param makeKey the constructor that makes an output item for an input item
   * @param collector a function that adds all of the “collected” input data to an output value
   * @return the grouped output stream
   */
  @RuntimeInterop
  public static <I, O> Stream<O> regroup(
      Stream<I> input, Function<I, O> makeKey, BiConsumer<O, I> collector) {
    final Map<O, List<I>> groups = input.collect(Collectors.groupingBy(makeKey));
    input.close();
    return groups
        .entrySet()
        .stream()
        .peek(e -> e.getValue().stream().forEach(x -> collector.accept(e.getKey(), x)))
        .map(Entry::getKey);
  }

  /** Clip the extension off a file path and return just the filename */
  public static String removeExtension(Path fileName, String extension) {
    final String fileNamePart = fileName.getFileName().toString();
    return fileNamePart.substring(0, fileNamePart.length() - extension.length());
  }

  /** Create a copy of a set less one item. */
  @RuntimeInterop
  public static <T> Set<T> removeItem(Imyhat type, Set<T> left, T right) {
    Set<T> result = type.newSet();
    result.addAll(left);
    result.remove(right);
    return result;
  }

  @RuntimeInterop
  public static Path resolvePath(Path path, String str) {
    return path.resolve(str);
  }

  public static <T> Stream<T> reverse(Stream<T> input) {
    final List<T> data = input.collect(Collectors.toList());
    Collections.reverse(data);
    return data.stream();
  }

  /** Stream an optional */
  @RuntimeInterop
  public static <T> Stream<T> stream(Optional<T> optional) {
    return optional.map(Stream::of).orElseGet(Stream::empty);
  }
  /** Stream a map */
  @RuntimeInterop
  public static Stream<Tuple> stream(Map<?, ?> map) {
    return map.entrySet().stream().map(e -> new Tuple(e.getKey(), e.getValue()));
  }

  @RuntimeInterop
  public static String toString(Instant instant, String format) {
    return DateTimeFormatter.ofPattern(format)
        .format(LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
  }

  /** Determine the union of two sets. */
  @RuntimeInterop
  public static <T> Set<T> union(Imyhat type, Set<T> left, Set<T> right) {
    Set<T> result = type.newSet();
    result.addAll(left);
    result.addAll(right);
    return result;
  }

  @RuntimeInterop
  public static <T> Optional<T> unnest(Optional<Optional<T>> input) {
    return input.orElse(Optional.empty());
  }

  @RuntimeInterop
  public static Optional<String> urlDecode(String input) {
    try {
      return Optional.of(URLDecoder.decode(input, "UTF-8"));
    } catch (UnsupportedEncodingException e) {
      return Optional.empty();
    }
  }

  @RuntimeInterop
  public static String urlEncode(String input) throws UnsupportedEncodingException {
    return URLEncoder.encode(input, "UTF-8").replace("*", "%2A");
  }

  @RuntimeInterop
  public static Stream<Tuple> zip(Set<Tuple> left, Set<Tuple> right, CopySemantics... semantics) {
    final Map<Object, Tuple> leftMap =
        left.stream().collect(Collectors.toMap(t -> t.get(0), Function.identity(), (a, b) -> a));
    final Map<Object, Tuple> rightMap =
        right.stream().collect(Collectors.toMap(t -> t.get(0), Function.identity(), (a, b) -> a));
    final Set<Object> keys = new HashSet<>();
    keys.addAll(leftMap.keySet());
    keys.addAll(rightMap.keySet());
    return keys.stream()
        .map(
            k -> {
              final Object[] output = new Object[semantics.length + 1];
              output[0] = k;
              final Tuple leftTuple = leftMap.getOrDefault(k, null);
              final Tuple rightTuple = rightMap.getOrDefault(k, null);
              for (int i = 0; i < semantics.length; i++) {
                output[i + 1] = semantics[i].apply(leftTuple, rightTuple);
              }
              return new Tuple(output);
            });
  }

  @RuntimeInterop public static final String[] EMPTY = new String[0];
  public static final ObjectMapper MAPPER = new ObjectMapper();

  @RuntimeInterop
  public static final BinaryOperator<?> USELESS_BINARY_OPERATOR =
      new BinaryOperator<Object>() {

        @Override
        public Object apply(Object t, Object u) {
          throw new UnsupportedOperationException();
        }
      };

  private static final Map<String, CallSite> callsites = new HashMap<>();

  static {
    MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    MAPPER.registerModule(new JavaTimeModule());
  }

  private RuntimeSupport() {}
}
