package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Matches to make Java's type checking produce correct types in Shesmu
 *
 * @param <T> the Java type being exported to Shesmu
 */
public abstract class TypeGuarantee<T> extends GenericTypeGuarantee<T> {
  public interface Pack2<T, U, R> {
    R pack(T first, U second);
  }

  public interface Pack3<T, U, V, R> {
    R pack(T first, U second, V third);
  }

  public interface Pack4<T, U, V, W, R> {
    R pack(T first, U second, V third, W fourth);
  }

  public interface Pack5<T, U, V, W, X, R> {
    R pack(T first, U second, V third, W fourth, X fifth);
  }

  @SafeVarargs
  public static <T> TypeGuarantee<T> algebraic(AlgebraicGuarantee<? extends T>... inner) {
    return algebraic(Stream.of(inner));
  }

  public static <T> TypeGuarantee<T> algebraic(Stream<AlgebraicGuarantee<? extends T>> inner) {
    final var processors =
        inner.collect(Collectors.toMap(AlgebraicGuarantee::name, Function.identity()));
    final var innerType =
        processors.values().stream().map(AlgebraicGuarantee::type).reduce(Imyhat::unify).get();
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return innerType;
      }

      @Override
      public T unpack(Object value) {
        final var algebraicValue = (AlgebraicValue) value;
        return processors.get(algebraicValue.name()).unpack(algebraicValue);
      }
    };
  }

  public static <E extends Enum<E>> TypeGuarantee<E> algebraicForEnum(Class<E> clazz) {
    return algebraic(
        Stream.of(clazz.getEnumConstants())
            .map(e -> AlgebraicGuarantee.empty(e.name().toUpperCase(), e)));
  }
  /**
   * Provides an argument mapping from a Shesmu dictionary/map into a Java map
   *
   * @param key the type guarantee for the keys; note that the conversion should preserve equality
   *     of the original values (<em>i.e.</em>, if two keys are not equal in Shesmu, then <code>K
   *     </code> should also be not equal) or collisions could occur
   * @param value the type guarantee for the values
   * @return a guarantee for a map
   */
  public static <K, V> TypeGuarantee<Map<K, V>> dictionary(
      TypeGuarantee<K> key, TypeGuarantee<V> value) {
    final var dictionaryType = Imyhat.dictionary(key.type(), value.type());
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return dictionaryType;
      }

      @Override
      public Map<K, V> unpack(Object object) {
        return ((Map<?, ?>) object)
            .entrySet().stream()
                .collect(
                    Collectors.toMap(e -> key.unpack(e.getKey()), e -> value.unpack(e.getValue())));
      }
    };
  }

  public static <T> TypeGuarantee<List<T>> list(TypeGuarantee<T> inner) {
    final var listType = inner.type().asList();
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return listType;
      }

      @Override
      public List<T> unpack(Object object) {
        return ((Set<?>) object).stream().map(inner::unpack).collect(Collectors.toList());
      }
    };
  }

  public static <T> TypeGuarantee<Optional<T>> optional(TypeGuarantee<T> inner) {
    final var listType = inner.type().asOptional();
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return listType;
      }

      @Override
      public Optional<T> unpack(Object object) {
        return ((Optional<?>) object).map(inner::unpack);
      }
    };
  }

  public static <R, T, U> TypeGuarantee<R> object(
      Pack2<? super T, ? super U, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second) {
    final Imyhat tupleType =
        new Imyhat.ObjectImyhat(
            Stream.of(new Pair<>(firstName, first.type()), new Pair<>(secondName, second.type())));
    if (firstName.compareTo(secondName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  public static <R, T, U, V> TypeGuarantee<R> object(
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third) {
    final Imyhat tupleType =
        new Imyhat.ObjectImyhat(
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type())));
    if (firstName.compareTo(secondName) >= 0 || secondName.compareTo(thirdName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  public static <R, T, U, V, W> TypeGuarantee<R> object(
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third,
      String fourthName,
      TypeGuarantee<W> fourth) {
    final Imyhat tupleType =
        new Imyhat.ObjectImyhat(
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type())));
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)));
      }
    };
  }

  public static <R, T, U, V, W, X> TypeGuarantee<R> object(
      Pack5<? super T, ? super U, ? super V, ? super W, ? super X, ? extends R> convert,
      String firstName,
      TypeGuarantee<T> first,
      String secondName,
      TypeGuarantee<U> second,
      String thirdName,
      TypeGuarantee<V> third,
      String fourthName,
      TypeGuarantee<W> fourth,
      String fifthName,
      TypeGuarantee<X> fifth) {
    final Imyhat tupleType =
        new Imyhat.ObjectImyhat(
            Stream.of(
                new Pair<>(firstName, first.type()),
                new Pair<>(secondName, second.type()),
                new Pair<>(thirdName, third.type()),
                new Pair<>(fourthName, fourth.type()),
                new Pair<>(fifthName, fifth.type())));
    if (firstName.compareTo(secondName) >= 0
        || secondName.compareTo(thirdName) >= 0
        || thirdName.compareTo(fourthName) >= 0
        || fourthName.compareTo(fifthName) >= 0) {
      throw new IllegalArgumentException("Object properties must be alphabetically ordered.");
    }
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)),
            fifth.unpack(input.get(4)));
      }
    };
  }

  public static <R, T, U> TypeGuarantee<R> tuple(
      Pack2<? super T, ? super U, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second) {
    final Imyhat tupleType = Imyhat.tuple(first.type(), second.type());
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(first.unpack(input.get(0)), second.unpack(input.get(1)));
      }
    };
  }

  public static <R, T, U, V> TypeGuarantee<R> tuple(
      Pack3<? super T, ? super U, ? super V, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third) {
    final Imyhat tupleType = Imyhat.tuple(first.type(), second.type(), third.type());
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(
            first.unpack(input.get(0)), second.unpack(input.get(1)), third.unpack(input.get(2)));
      }
    };
  }

  public static <R, T, U, V, W> TypeGuarantee<R> tuple(
      Pack4<? super T, ? super U, ? super V, ? super W, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second,
      TypeGuarantee<V> third,
      TypeGuarantee<W> fourth) {
    final Imyhat tupleType = Imyhat.tuple(first.type(), second.type(), third.type(), fourth.type());
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final var input = (Tuple) object;
        return convert.pack(
            first.unpack(input.get(0)),
            second.unpack(input.get(1)),
            third.unpack(input.get(2)),
            fourth.unpack(input.get(3)));
      }
    };
  }

  public static TypeGuarantee<Tuple> tuple(Imyhat... elements) {
    final Imyhat tupleType = Imyhat.tuple(elements);
    return new TypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public Tuple unpack(Object object) {
        return (Tuple) object;
      }
    };
  }

  public static final TypeGuarantee<Boolean> BOOLEAN =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }

        @Override
        public Boolean unpack(Object object) {
          return (Boolean) object;
        }
      };
  public static final TypeGuarantee<Instant> DATE =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.DATE;
        }

        @Override
        public Instant unpack(Object object) {
          return (Instant) object;
        }
      };
  public static final TypeGuarantee<Double> DOUBLE =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.FLOAT;
        }

        @Override
        public Double unpack(Object object) {
          return (Double) object;
        }
      };
  /**
   * Convert a Shesmu JSON value into a Jackson structure
   *
   * <p>Note that the converted JSON object must <em>not</em> be mutated by the recipient or the
   * olive might break as olives assume all values are immutable.
   */
  public static final TypeGuarantee<JsonNode> JSON =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.JSON;
        }

        @Override
        public JsonNode unpack(Object object) {
          return (JsonNode) object;
        }
      };

  public static final TypeGuarantee<Long> LONG =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }

        @Override
        public Long unpack(Object object) {
          return (Long) object;
        }
      };
  public static final TypeGuarantee<Path> PATH =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }

        @Override
        public Path unpack(Object object) {
          return (Path) object;
        }
      };
  public static final TypeGuarantee<String> STRING =
      new TypeGuarantee<>() {
        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }

        @Override
        public String unpack(Object object) {
          return (String) object;
        }
      };

  private TypeGuarantee() {}

  @Override
  public final <R> R apply(GenericTransformer<R> transformer) {
    return type().apply(transformer);
  }

  @Override
  public final boolean check(Map<String, Imyhat> variables, Imyhat reference) {
    return type().isAssignableFrom(reference);
  }

  @Override
  public final Imyhat render(Map<String, Imyhat> variables) {
    return type();
  }

  @Override
  public final String toString(Map<String, Imyhat> typeVariables) {
    return type().name();
  }

  public abstract Imyhat type();

  public abstract T unpack(Object value);
}
