package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Matches to make Java's type checking produce correct types in Shesmu
 *
 * <p>This class doesn't do anything per-se, it just allows Java type checker to produce a type that
 * is compatible with a Shesmu {@link Imyhat} without much work on the part of the author
 *
 * @param <T> the Java type being exported to Shesmu
 */
public abstract class ReturnTypeGuarantee<T> extends GenericReturnTypeGuarantee<T> {
  public static <T> ReturnTypeGuarantee<Set<T>> list(ReturnTypeGuarantee<T> inner) {
    final var listType = inner.type().asList();
    return new ReturnTypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return listType;
      }
    };
  }

  public static <T> ReturnTypeGuarantee<Optional<T>> optional(ReturnTypeGuarantee<T> inner) {
    final var optionalType = inner.type().asOptional();
    return new ReturnTypeGuarantee<>() {
      @Override
      public Imyhat type() {
        return optionalType;
      }
    };
  }

  public static final ReturnTypeGuarantee<Boolean> BOOLEAN =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  public static final ReturnTypeGuarantee<Instant> DATE =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.DATE;
        }
      };
  public static final ReturnTypeGuarantee<Double> DOUBLE =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.FLOAT;
        }
      };
  public static final ReturnTypeGuarantee<Long> LONG =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  public static final ReturnTypeGuarantee<Path> PATH =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }
      };
  public static final ReturnTypeGuarantee<String> STRING =
      new ReturnTypeGuarantee<>() {

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };

  ReturnTypeGuarantee() {}

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
}
