package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Matches to make Java's type checking produce correct types in Shesmu and can include generic
 * types
 *
 * @param <T> the Java type being exported to Shesmu
 */
public abstract class GenericTypeGuarantee<T> {

  public static <T> GenericTypeGuarantee<T> genericAlgebraic(
      GenericAlgebraicGuarantee<? extends T>... inner) {
    final Map<String, GenericAlgebraicGuarantee<? extends T>> processors =
        Stream.of(inner)
            .collect(Collectors.toMap(GenericAlgebraicGuarantee::name, Function.identity()));
    return new GenericTypeGuarantee<T>() {

      @Override
      public <R> R apply(GenericTransformer<R> transformer) {
        return transformer.genericAlgebraic(processors.values().stream());
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        return reference.apply(
            new ImyhatTransformer<Boolean>() {
              @Override
              public Boolean algebraic(Stream<AlgebraicTransformer> contents) {
                return contents.allMatch(
                    t -> {
                      final GenericAlgebraicGuarantee<? extends T> processor =
                          processors.get(t.name());
                      return processor != null && processor.check(variables, t);
                    });
              }

              @Override
              public Boolean bool() {
                return false;
              }

              @Override
              public Boolean date() {
                return false;
              }

              @Override
              public Boolean floating() {
                return false;
              }

              @Override
              public Boolean integer() {
                return false;
              }

              @Override
              public Boolean json() {
                return false;
              }

              @Override
              public Boolean list(Imyhat inner) {
                return false;
              }

              @Override
              public Boolean map(Imyhat key, Imyhat value) {
                return false;
              }

              @Override
              public Boolean object(Stream<Pair<String, Imyhat>> contents) {
                return false;
              }

              @Override
              public Boolean optional(Imyhat inner) {
                return false;
              }

              @Override
              public Boolean path() {
                return false;
              }

              @Override
              public Boolean string() {
                return false;
              }

              @Override
              public Boolean tuple(Stream<Imyhat> contents) {
                return false;
              }
            });
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return processors
            .values()
            .stream()
            .map(v -> v.render(variables))
            .reduce(Imyhat::unify)
            .get();
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return processors
            .values()
            .stream()
            .map(v -> v.toString(typeVariables))
            .collect(Collectors.joining(" | "));
      }

      @Override
      public T unpack(Object value) {
        final AlgebraicValue algebraicValue = (AlgebraicValue) value;
        return processors.get(algebraicValue.name()).unpack(algebraicValue);
      }
    };
  }
  /**
   * Create a list containing a generic type
   *
   * @param inner the type of the contents of the list
   */
  public static <T> GenericTypeGuarantee<Set<T>> genericList(GenericTypeGuarantee<T> inner) {
    return new GenericTypeGuarantee<Set<T>>() {

      @Override
      public <R> R apply(GenericTransformer<R> transformer) {
        return transformer.genericList(inner);
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        return reference instanceof Imyhat.ListImyhat
            && inner.check(variables, ((Imyhat.ListImyhat) reference).inner());
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return inner.render(variables).asList();
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return "[" + inner.toString(typeVariables) + "]";
      }

      @Override
      public Set<T> unpack(Object object) {
        return ((Set<?>) object).stream().map(inner::unpack).collect(Collectors.toSet());
      }
    };
  }

  /**
   * Create a list containing a generic type
   *
   * @param key the type of the keys of the map
   * @param value the type of the values of the map
   */
  public static <K, V> GenericTypeGuarantee<Map<K, V>> genericMap(
      GenericTypeGuarantee<K> key, GenericTypeGuarantee<V> value) {
    return new GenericTypeGuarantee<Map<K, V>>() {

      @Override
      public <R> R apply(GenericTransformer<R> transformer) {
        return transformer.genericMap(key, value);
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        return reference instanceof Imyhat.DictionaryImyhat
            && key.check(variables, ((Imyhat.DictionaryImyhat) reference).key())
            && value.check(variables, ((Imyhat.DictionaryImyhat) reference).value());
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.dictionary(key.render(variables), value.render(variables));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return "<" + key.toString(typeVariables) + ":" + value.toString(typeVariables) + ">";
      }

      @Override
      public Map<K, V> unpack(Object object) {
        return ((Map<?, ?>) object)
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(e -> key.unpack(e.getKey()), e -> value.unpack(e.getValue())));
      }
    };
  }

  /**
   * Create a list containing a generic type
   *
   * @param inner the type of the contents of the list
   */
  public static <T> GenericTypeGuarantee<Optional<T>> genericOptional(
      GenericTypeGuarantee<T> inner) {
    return new GenericTypeGuarantee<Optional<T>>() {

      @Override
      public <R> R apply(GenericTransformer<R> transformer) {
        return transformer.genericOptional(inner);
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        return reference == Imyhat.NOTHING
            || reference instanceof Imyhat.OptionalImyhat
                && inner.check(variables, ((Imyhat.OptionalImyhat) reference).inner());
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return inner.render(variables).asOptional();
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return inner.toString(typeVariables) + "?";
      }

      @Override
      public Optional<T> unpack(Object object) {
        return ((Optional<?>) object).map(inner::unpack);
      }
    };
  }

  /**
   * Create a tuple that contains generic types
   *
   * @param pack a function to convert the tuple's value to the preferred Java type
   * @param first the type of the first element in the tuple
   * @param second the type of the second element in the tuple
   * @param <R> the type of value after conversion to a Java type
   * @param <T> the type of the first element of the tuple
   * @param <U> the type of the second element of the tuple
   */
  public static <R, T, U> GenericTypeGuarantee<R> genericTuple(
      TypeGuarantee.Pack2<? super T, ? super U, ? extends R> pack,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second) {
    return new GenericTypeGuarantee<R>() {
      @Override
      public <O> O apply(GenericTransformer<O> transformer) {
        return transformer.genericTuple(Stream.of(first, second));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        if (reference instanceof Imyhat.TupleImyhat) {
          Imyhat.TupleImyhat tuple = (Imyhat.TupleImyhat) reference;
          return first.check(variables, tuple.get(0)) && second.check(variables, tuple.get(1));
        }
        return false;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.tuple(
            Stream.of(first, second).map(e -> e.render(variables)).toArray(Imyhat[]::new));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return Stream.of(first, second)
            .map(e -> e.toString(typeVariables))
            .collect(Collectors.joining(", ", "{", "}"));
      }

      @Override
      public R unpack(Object object) {
        final Tuple tuple = (Tuple) object;
        return pack.pack(first.unpack(tuple.get(0)), second.unpack(tuple.get(1)));
      }
    };
  }

  /**
   * Create a tuple that contains generic types
   *
   * @param pack a function to convert the tuple's value to the preferred Java type
   * @param first the type of the first element in the tuple
   * @param second the type of the second element in the tuple
   * @param third the type of the third element in the tuple
   * @param <R> the type of value after convertion to a Java type
   * @param <T> the type of the first element of the tuple
   * @param <U> the type of the second element of the tuple
   * @param <V> the type of the third element of the tuple
   */
  public static <R, T, U, V> GenericTypeGuarantee<R> genericTuple(
      TypeGuarantee.Pack3<? super T, ? super U, ? super V, ? extends R> pack,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second,
      GenericTypeGuarantee<V> third) {
    return new GenericTypeGuarantee<R>() {
      @Override
      public <O> O apply(GenericTransformer<O> transformer) {
        return transformer.genericTuple(Stream.of(first, second, third));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        if (reference instanceof Imyhat.TupleImyhat) {
          Imyhat.TupleImyhat tuple = (Imyhat.TupleImyhat) reference;
          return first.check(variables, tuple.get(0))
              && second.check(variables, tuple.get(1))
              && third.check(variables, tuple.get(2));
        }
        return false;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.tuple(
            Stream.of(first, second, third).map(e -> e.render(variables)).toArray(Imyhat[]::new));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return Stream.of(first, second, third)
            .map(e -> e.toString(typeVariables))
            .collect(Collectors.joining(", ", "{", "}"));
      }

      @Override
      public R unpack(Object object) {
        final Tuple tuple = (Tuple) object;
        return pack.pack(
            first.unpack(tuple.get(0)), second.unpack(tuple.get(1)), third.unpack(tuple.get(2)));
      }
    };
  }

  /**
   * Create a tuple that contains generic types
   *
   * @param pack a function to convert the tuple's value to the preferred Java type
   * @param first the type of the first element in the tuple
   * @param second the type of the second element in the tuple
   * @param third the type of the third element in the tuple
   * @param fourth the type of the third element in the tuple
   * @param <R> the type of value after convertion to a Java type
   * @param <T> the type of the first element of the tuple
   * @param <U> the type of the second element of the tuple
   * @param <W> the type of the fourth element of the tuple
   * @param <V> the type of the third element of the tuple
   */
  public static <R, T, U, W, V> GenericTypeGuarantee<R> genericTuple(
      TypeGuarantee.Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> pack,
      GenericTypeGuarantee<T> first,
      GenericTypeGuarantee<U> second,
      GenericTypeGuarantee<V> third,
      GenericTypeGuarantee<W> fourth) {
    return new GenericTypeGuarantee<R>() {
      @Override
      public <O> O apply(GenericTransformer<O> transformer) {
        return transformer.genericTuple(Stream.of(first, second, third, fourth));
      }

      @Override
      public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
        if (reference instanceof Imyhat.TupleImyhat) {
          Imyhat.TupleImyhat tuple = (Imyhat.TupleImyhat) reference;
          return first.check(variables, tuple.get(0))
              && second.check(variables, tuple.get(1))
              && third.check(variables, tuple.get(2))
              && fourth.check(variables, tuple.get(3));
        }
        return false;
      }

      @Override
      public Imyhat render(Map<String, Imyhat> variables) {
        return Imyhat.tuple(
            Stream.of(first, second, third, fourth)
                .map(e -> e.render(variables))
                .toArray(Imyhat[]::new));
      }

      @Override
      public String toString(Map<String, Imyhat> typeVariables) {
        return Stream.of(first, second, third, fourth)
            .map(e -> e.toString(typeVariables))
            .collect(Collectors.joining(", ", "{", "}"));
      }

      @Override
      public R unpack(Object object) {
        final Tuple tuple = (Tuple) object;
        return pack.pack(
            first.unpack(tuple.get(0)),
            second.unpack(tuple.get(1)),
            third.unpack(tuple.get(2)),
            fourth.unpack(tuple.get(3)));
      }
    };
  }

  GenericTypeGuarantee() {}

  public abstract <R> R apply(GenericTransformer<R> transformer);

  public abstract boolean check(Map<String, Imyhat> variables, Imyhat reference);

  public abstract Imyhat render(Map<String, Imyhat> variables);

  public abstract String toString(Map<String, Imyhat> typeVariables);

  public abstract T unpack(Object object);
}
