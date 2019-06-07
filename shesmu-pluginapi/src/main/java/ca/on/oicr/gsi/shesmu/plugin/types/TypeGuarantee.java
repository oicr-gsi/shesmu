package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

  public static <T> TypeGuarantee<List<T>> list(TypeGuarantee<T> inner) {
    final Imyhat listType = inner.type().asList();
    return new TypeGuarantee<List<T>>() {
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

  public static <R, T, U> TypeGuarantee<R> tuple(
      Pack2<? super T, ? super U, ? extends R> convert,
      TypeGuarantee<T> first,
      TypeGuarantee<U> second) {
    final Imyhat tupleType = Imyhat.tuple(first.type(), second.type());
    return new TypeGuarantee<R>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final Tuple input = (Tuple) object;
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
    return new TypeGuarantee<R>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final Tuple input = (Tuple) object;
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
    return new TypeGuarantee<R>() {
      @Override
      public Imyhat type() {
        return tupleType;
      }

      @Override
      public R unpack(Object object) {
        final Tuple input = (Tuple) object;
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
    return new TypeGuarantee<Tuple>() {
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
      new TypeGuarantee<Boolean>() {
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
      new TypeGuarantee<Instant>() {
        @Override
        public Imyhat type() {
          return Imyhat.DATE;
        }

        @Override
        public Instant unpack(Object object) {
          return (Instant) object;
        }
      };
  public static final TypeGuarantee<Long> LONG =
      new TypeGuarantee<Long>() {
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
      new TypeGuarantee<Path>() {
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
      new TypeGuarantee<String>() {
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
    return type().isSame(reference);
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
