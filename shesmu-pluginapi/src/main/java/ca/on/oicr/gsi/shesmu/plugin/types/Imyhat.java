package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Types as represented in Shesmu
 *
 * <p>This word means “which/pattern of conduct” in ancient Egyptian to avoid naming conflicts with
 * other classes named type.
 *
 * <p>Java's {@link Class} are unsuitable for this purpose because generic erasure has happened.
 * Shesmu types also have an interchange string format, called a descriptor.
 */
public abstract class Imyhat {
  /** A subclass of types for base types */
  public abstract static class BaseImyhat extends Imyhat {

    public abstract Object defaultValue();

    @Override
    public final boolean isBad() {
      return false;
    }

    @Override
    public boolean isSame(Imyhat other) {
      return this == other;
    }

    /** Gets the type of the wrapper (i.e., the reference type) */
    public abstract Class<?> javaWrapperType();

    /**
     * Parse a string literal containing a value of this type
     *
     * @param input the string value
     * @return the result as an object, or null if an error occurs
     */
    public abstract Object parse(String input);
  }

  public static final class ListImyhat extends Imyhat {
    private final Imyhat inner;

    private ListImyhat(Imyhat inner) {
      this.inner = inner;
    }

    @Override
    public void accept(ImyhatConsumer dispatcher, Object value) {
      @SuppressWarnings("unchecked")
      final Set<Object> values = (Set<Object>) value;
      dispatcher.accept(values.stream(), inner);
    }

    @Override
    public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
      @SuppressWarnings("unchecked")
      final Set<Object> values = (Set<Object>) value;
      return dispatcher.apply(values.stream(), inner);
    }

    @Override
    public <R> R apply(ImyhatTransformer<R> transformer) {
      return transformer.list(inner);
    }

    @Override
    public Comparator<?> comparator() {
      @SuppressWarnings("unchecked")
      final Comparator<Object> innerComparator = (Comparator<Object>) inner.comparator();
      return (Set<?> a, Set<?> b) -> {
        final Iterator<?> aIt = a.iterator();
        final Iterator<?> bIt = b.iterator();
        while (aIt.hasNext() && bIt.hasNext()) {
          final int result = innerComparator.compare(aIt.next(), bIt.next());
          if (result != 0) {
            return result;
          }
        }
        return Boolean.compare(aIt.hasNext(), bIt.hasNext());
      };
    }

    @Override
    public String descriptor() {
      return "a" + inner.descriptor();
    }

    public Imyhat inner() {
      return inner;
    }

    @Override
    public boolean isBad() {
      return inner.isBad();
    }

    @Override
    public boolean isOrderable() {
      return false;
    }

    @Override
    public boolean isSame(Imyhat other) {
      if (other instanceof ListImyhat) {
        return inner.isSame(((ListImyhat) other).inner);
      }
      return false;
    }

    @Override
    public Class<?> javaType() {
      return Set.class;
    }

    @Override
    public String name() {
      return "[" + inner.name() + "]";
    }
  }

  public static final class ObjectImyhat extends Imyhat {

    private final Map<String, Pair<Imyhat, Integer>> fields = new TreeMap<>();

    public ObjectImyhat(Stream<Pair<String, Imyhat>> fields) {
      fields
          .sorted(Comparator.comparing(Pair::first))
          .forEach(
              new Consumer<Pair<String, Imyhat>>() {
                int index;

                @Override
                public void accept(Pair<String, Imyhat> pair) {
                  ObjectImyhat.this.fields.put(pair.first(), new Pair<>(pair.second(), index++));
                }
              });
    }

    @Override
    public void accept(ImyhatConsumer dispatcher, Object value) {
      final Tuple tuple = (Tuple) value;
      dispatcher.acceptObject(
          fields
              .entrySet()
              .stream()
              .map(
                  e ->
                      new Field<>(
                          e.getKey(), tuple.get(e.getValue().second()), e.getValue().first())));
    }

    @Override
    public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
      final Tuple tuple = (Tuple) value;
      return dispatcher.applyObject(
          fields
              .entrySet()
              .stream()
              .map(
                  e ->
                      new Field<>(
                          e.getKey(), tuple.get(e.getValue().second()), e.getValue().first())));
    }

    @Override
    public <R> R apply(ImyhatTransformer<R> transformer) {
      return transformer.object(
          fields.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue().first())));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Comparator<?> comparator() {
      return fields
          .values()
          .stream()
          .sorted(Comparator.comparing(Pair::second))
          .map(
              p ->
                  Comparator.comparing(
                      (Tuple t) -> t.get(p.second()), (Comparator<Object>) p.first().comparator()))
          .reduce(Comparator::thenComparing)
          .get();
    }

    @Override
    public String descriptor() {
      return "o"
          + fields.size()
          + fields
              .entrySet()
              .stream()
              .map(e -> e.getKey() + "$" + e.getValue().first().descriptor())
              .collect(Collectors.joining());
    }

    public Stream<Map.Entry<String, Pair<Imyhat, Integer>>> fields() {
      return fields.entrySet().stream();
    }

    public Imyhat get(String field) {
      return fields.getOrDefault(field, new Pair<>(BAD, 0)).first();
    }

    public int index(String field) {
      return fields.getOrDefault(field, new Pair<>(BAD, 0)).second();
    }

    @Override
    public boolean isBad() {
      return fields.values().stream().map(Pair::first).anyMatch(Imyhat::isBad);
    }

    @Override
    public boolean isOrderable() {
      return false;
    }

    @Override
    public boolean isSame(Imyhat other) {
      if (!(other instanceof ObjectImyhat)) {
        return false;
      }
      final Map<String, Pair<Imyhat, Integer>> otherFields = ((ObjectImyhat) other).fields;
      if (fields.size() != otherFields.size()) {
        return false;
      }
      return fields
          .entrySet()
          .stream()
          .allMatch(
              e ->
                  otherFields
                      .getOrDefault(e.getKey(), new Pair<>(Imyhat.BAD, 0))
                      .first()
                      .isSame(e.getValue().first()));
    }

    @Override
    public Class<?> javaType() {
      return Tuple.class;
    }

    @Override
    public String name() {
      return fields
          .entrySet()
          .stream()
          .map(e -> e.getKey() + " = " + e.getValue().first().name())
          .sorted()
          .collect(Collectors.joining(",", "{ ", " }"));
    }
  }

  public static final class TupleImyhat extends Imyhat {
    private final Imyhat[] types;

    private TupleImyhat(Imyhat[] types) {
      this.types = types;
    }

    @Override
    public void accept(ImyhatConsumer dispatcher, Object value) {
      final Tuple tuple = (Tuple) value;
      dispatcher.acceptTuple(
          IntStream.range(0, types.length).mapToObj(i -> new Field<>(i, tuple.get(i), types[i])));
    }

    @Override
    public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
      final Tuple tuple = (Tuple) value;
      return dispatcher.applyTuple(
          IntStream.range(0, types.length).mapToObj(i -> new Field<>(i, tuple.get(i), types[i])));
    }

    @Override
    public <R> R apply(ImyhatTransformer<R> transformer) {
      return transformer.tuple(Stream.of(types));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Comparator<?> comparator() {
      Comparator<Tuple> comparator =
          Comparator.comparing((Tuple t) -> t.get(0), (Comparator<Object>) types[0].comparator());
      for (int i = 1; i < types.length; i++) {
        final int index = i;
        comparator =
            comparator.thenComparing(
                (Tuple t) -> t.get(index), (Comparator<Object>) types[index].comparator());
      }
      return comparator;
    }

    @Override
    public String descriptor() {
      return Arrays.stream(types)
          .map(Imyhat::descriptor)
          .collect(Collectors.joining("", "t" + types.length, ""));
    }

    public Imyhat get(int index) {
      return index >= 0 && index < types.length ? types[index] : BAD;
    }

    public Stream<Imyhat> inner() {
      return Stream.of(types);
    }

    @Override
    public boolean isBad() {
      return Arrays.stream(types).anyMatch(Imyhat::isBad);
    }

    @Override
    public boolean isOrderable() {
      return false;
    }

    @Override
    public boolean isSame(Imyhat other) {
      if (other instanceof TupleImyhat) {
        final Imyhat[] others = ((TupleImyhat) other).types;
        if (others.length != types.length) {
          return false;
        }
        for (int i = 0; i < types.length; i++) {
          if (!others[i].isSame(types[i])) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    @Override
    public Class<?> javaType() {
      return Tuple.class;
    }

    @Override
    public String name() {
      return Arrays.stream(types).map(Imyhat::name).collect(Collectors.joining(", ", "{", "}"));
    }
  }

  public static Stream<BaseImyhat> baseTypes() {
    return Stream.of(BOOLEAN, DATE, FLOAT, INTEGER, PATH, STRING);
  }

  /**
   * A bootstrap method that returns the appropriate {@link Imyhat} from a descriptor.
   *
   * @param descriptor the method name, which is the type descriptor; descriptor are guaranteed to
   *     be valid JVM identifiers
   * @param type the type of this call site, which must take no arguments and return {@link Imyhat}
   */
  @SuppressWarnings("unused")
  public static CallSite bootstrap(Lookup lookup, String descriptor, MethodType type) {
    if (!type.returnType().equals(Imyhat.class)) {
      throw new IllegalArgumentException("Method cannot return non-Imyhat type.");
    }
    if (type.parameterCount() != 0) {
      throw new IllegalArgumentException("Method cannot take parameters.");
    }
    if (callsites.containsKey(descriptor)) {
      return callsites.get(descriptor);
    }
    final Imyhat imyhat = parse(descriptor);
    if (imyhat.isBad()) {
      throw new IllegalArgumentException("Bad type descriptor: " + descriptor);
    }
    final CallSite callsite = new ConstantCallSite(MethodHandles.constant(Imyhat.class, imyhat));
    callsites.put(descriptor, callsite);
    return callsite;
  }

  /**
   * Convert a possibly annotated Java type into a Shesmu type
   *
   * @param context the location to be displayed in error messages
   * @param descriptor the annotated Shesmu descriptor
   * @param clazz the class of the type
   */
  public static Imyhat convert(String context, String descriptor, Type clazz) {
    if (descriptor.isEmpty()) {
      return Imyhat.of(clazz)
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      String.format(
                          "%s has no type annotation and %s type isn't a valid Shesmu type.",
                          context, clazz.getTypeName())));
    } else {
      final Imyhat type = Imyhat.parse(descriptor);
      if (type.isBad()) {
        throw new IllegalArgumentException(
            String.format("%s has invalid type descriptor %s", context, descriptor));
      }
      if (!type.javaType().equals(TypeFactory.rawClass(clazz))) {
        throw new IllegalArgumentException(
            String.format(
                "%s has Java type %s but Shesmu type descriptor implies %s.",
                context, clazz.getTypeName(), type.javaType()));
      }
      return type;
    }
  }

  /** Parse a name which must be one of the base types (no lists or tuples) */
  public static BaseImyhat forName(String s) {
    return baseTypes()
        .filter(t -> t.name().equals(s))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException(String.format("No such base type %s.", s)));
  }

  public static Optional<? extends Imyhat> of(Type c) {
    final Optional<Imyhat> baseType =
        baseTypes()
            .filter(t -> t.javaType().equals(c) || t.javaWrapperType().equals(c))
            .findAny()
            .map(x -> x);
    if (baseType.isPresent()) {
      return baseType;
    }
    System.err.println(c.getTypeName());
    System.err.println(c.getClass().getName());
    if (!(c instanceof ParameterizedType)) {
      return Optional.empty();
    }
    final ParameterizedType p = (ParameterizedType) c;
    // We check for equals rather than isAssignableFrom because we don't know which way the
    // assignment is going
    if (p.getActualTypeArguments().length != 1
        || !(p.getRawType() instanceof Class<?>)
        || !Set.class.equals(p.getRawType())) {
      System.err.println(Arrays.asList(p.getActualTypeArguments()));
      System.err.println(p.getRawType());
      System.err.println(p.getRawType().getClass());
      return Optional.empty();
    }
    return of(p.getActualTypeArguments()[0]).map(Imyhat::asList);
  }

  /**
   * Parse a string-representation of a type
   *
   * @param input the Shesmu string (as generated by {@link #descriptor()}
   * @return the parsed type; if the type is malformed, {@link #BAD} is returned
   */
  public static Imyhat parse(CharSequence input) {
    final AtomicReference<CharSequence> output = new AtomicReference<>();
    final Imyhat result = parse(input, output);
    return output.get().length() == 0 ? result : BAD;
  }

  /**
   * Parse a descriptor and return the corresponding type
   *
   * @param input the Shesmu string (as generated by {@link #descriptor()}
   * @param output the remaining subsequence of the input after parsing
   * @return the parsed type; if the type is malformed, {@link #BAD} is returned
   */
  public static Imyhat parse(CharSequence input, AtomicReference<CharSequence> output) {
    if (input.length() == 0) {
      output.set(input);
      return BAD;
    }
    switch (input.charAt(0)) {
      case 'b':
        output.set(input.subSequence(1, input.length()));
        return BOOLEAN;
      case 'd':
        output.set(input.subSequence(1, input.length()));
        return DATE;
      case 'i':
        output.set(input.subSequence(1, input.length()));
        return INTEGER;
      case 'p':
        output.set(input.subSequence(1, input.length()));
        return PATH;
      case 's':
        output.set(input.subSequence(1, input.length()));
        return STRING;
      case 'a':
        return parse(input.subSequence(1, input.length()), output).asList();
      case 't':
      case 'o':
        int count = 0;
        int index;
        for (index = 1; Character.isDigit(input.charAt(index)); index++) {
          count = 10 * count + Character.digit(input.charAt(index), 10);
        }
        if (count == 0) {
          return BAD;
        }
        output.set(input.subSequence(index, input.length()));
        if (input.charAt(0) == 't') {
          final Imyhat[] inner = new Imyhat[count];
          for (int i = 0; i < count; i++) {
            inner[i] = parse(output.get(), output);
          }
          return tuple(inner);
        } else {
          final List<Pair<String, Imyhat>> fields = new ArrayList<>();
          for (int i = 0; i < count; i++) {
            final StringBuilder name = new StringBuilder();
            int dollar = 0;
            while (output.get().charAt(dollar) != '$') {
              name.append(output.get().charAt(dollar));
              dollar++;
            }
            output.set(output.get().subSequence(dollar + 1, output.get().length()));
            fields.add(new Pair<>(name.toString(), parse(output.get(), output)));
          }
          return new ObjectImyhat(fields.stream());
        }
      default:
        output.set(input);
        return BAD;
    }
  }

  /**
   * Create a tuple type from the types of its elements.
   *
   * @param types the element types, in order
   */
  public static Imyhat tuple(Imyhat... types) {
    return new TupleImyhat(types);
  }

  public static final Imyhat BAD =
      new Imyhat() {
        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          throw new UnsupportedOperationException("Cannot handle value of bad type.");
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          throw new UnsupportedOperationException("Cannot handle value of bad type.");
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return null;
        }

        @Override
        public Comparator<?> comparator() {
          return (a, b) -> 0;
        }

        @Override
        public String descriptor() {
          return "$";
        }

        @Override
        public boolean isBad() {
          return true;
        }

        @Override
        public boolean isOrderable() {
          return false;
        }

        @Override
        public boolean isSame(Imyhat other) {
          return false;
        }

        @Override
        public Class<?> javaType() {
          return Object.class;
        }

        @Override
        public String name() {
          return "💩";
        }
      };

  public static final BaseImyhat BOOLEAN =
      new BaseImyhat() {

        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((Boolean) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((Boolean) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.bool();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return false;
        }

        @Override
        public String descriptor() {
          return "b";
        }

        @Override
        public boolean isOrderable() {
          return false;
        }

        @Override
        public Class<?> javaType() {
          return boolean.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return Boolean.class;
        }

        @Override
        public String name() {
          return "boolean";
        }

        @Override
        public Object parse(String s) {
          return "true".equals(s);
        }
      };
  public static final BaseImyhat DATE =
      new BaseImyhat() {
        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((Instant) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((Instant) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.date();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return Instant.EPOCH;
        }

        @Override
        public String descriptor() {
          return "d";
        }

        @Override
        public boolean isOrderable() {
          return true;
        }

        @Override
        public Class<?> javaType() {
          return Instant.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return Instant.class;
        }

        @Override
        public String name() {
          return "date";
        }

        @Override
        public Object parse(String s) {
          return ZonedDateTime.parse(s).toInstant();
        }
      };
  public static final BaseImyhat FLOAT =
      new BaseImyhat() {
        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((Double) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((Double) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.floating();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return 0.0;
        }

        @Override
        public String descriptor() {
          return "f";
        }

        @Override
        public boolean isOrderable() {
          return true;
        }

        @Override
        public Class<?> javaType() {
          return double.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return Double.class;
        }

        @Override
        public String name() {
          return "float";
        }

        @Override
        public Object parse(String s) {
          return ZonedDateTime.parse(s).toInstant();
        }
      };
  public static final BaseImyhat INTEGER =
      new BaseImyhat() {
        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((Long) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((Long) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.integer();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return 0L;
        }

        @Override
        public String descriptor() {
          return "i";
        }

        @Override
        public boolean isOrderable() {
          return true;
        }

        @Override
        public Class<?> javaType() {
          return long.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return Long.class;
        }

        @Override
        public String name() {
          return "integer";
        }

        @Override
        public Object parse(String s) {
          return Long.parseLong(s);
        }
      };
  public static final BaseImyhat PATH =
      new BaseImyhat() {

        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((Path) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((Path) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.path();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return Paths.get(".");
        }

        @Override
        public String descriptor() {
          return "p";
        }

        @Override
        public boolean isOrderable() {
          return false;
        }

        @Override
        public Class<?> javaType() {
          return Path.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return Path.class;
        }

        @Override
        public String name() {
          return "path";
        }

        @Override
        public Object parse(String s) {
          return Paths.get(s);
        }
      };
  public static final BaseImyhat STRING =
      new BaseImyhat() {
        @Override
        public void accept(ImyhatConsumer dispatcher, Object value) {
          dispatcher.accept((String) value);
        }

        @Override
        public <R> R apply(ImyhatFunction<R> dispatcher, Object value) {
          return dispatcher.apply((String) value);
        }

        @Override
        public <R> R apply(ImyhatTransformer<R> transformer) {
          return transformer.string();
        }

        @Override
        public Comparator<?> comparator() {
          return Comparator.naturalOrder();
        }

        @Override
        public Object defaultValue() {
          return "";
        }

        @Override
        public String descriptor() {
          return "s";
        }

        @Override
        public boolean isOrderable() {
          return false;
        }

        @Override
        public Class<?> javaType() {
          return String.class;
        }

        @Override
        public Class<?> javaWrapperType() {
          return String.class;
        }

        @Override
        public String name() {
          return "string";
        }

        @Override
        public Object parse(String s) {
          return s;
        }
      };
  private static final Map<String, CallSite> callsites = new HashMap<>();

  /**
   * Unbox a value of this type
   *
   * @param dispatcher the acceptor for the different possible values
   * @param value the value to be unboxed
   */
  public abstract void accept(ImyhatConsumer dispatcher, Object value);

  /**
   * Unbox a value of this type and convert it to a result
   *
   * @param dispatcher the converters for the different possible values
   * @param value the value to be unboxed
   */
  public abstract <R> R apply(ImyhatFunction<R> dispatcher, Object value);

  /**
   * Transform this Shemsu type into a another representation
   *
   * @param transformer the converter for each type
   */
  public abstract <R> R apply(ImyhatTransformer<R> transformer);
  /** Create a list type containing the current type. */
  public final Imyhat asList() {
    return new ListImyhat(this);
  }

  /** Create a comparator for sorting sets. */
  public abstract Comparator<?> comparator();

  /**
   * Create a machine-friendly string describing this type.
   *
   * @see #parse(CharSequence)
   */
  public abstract String descriptor();

  /** Check if this type is malformed */
  public abstract boolean isBad();

  /**
   * Check if this type can be ordered.
   *
   * <p>It must implement the {@link Comparable} interface.
   */
  public abstract boolean isOrderable();

  /**
   * Checks if two types are the same.
   *
   * <p>This is not the same as {@link #equals(Object)} since bad types are never equivalent.
   */
  public abstract boolean isSame(Imyhat other);

  /**
   * Get the matching Java type for this type
   *
   * <p>The Java type will be less descriptive than this type due to erasure.
   */
  public abstract Class<?> javaType();

  /** Create a human-friendly string describing this type. */
  public abstract String name();

  public <T> Set<T> newSet() {
    @SuppressWarnings("unchecked")
    final Comparator<T> comparator = (Comparator<T>) comparator();
    return new TreeSet<>(comparator);
  }

  @SuppressWarnings("unchecked")
  public final <T> Collector<T, ?, TreeSet<T>> toSet() {
    final Comparator<T> comparator = (Comparator<T>) comparator();
    return Collectors.toCollection(() -> new TreeSet<T>(comparator));
  }

  @Override
  public final String toString() {
    return descriptor();
  }
}
