package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
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
    final Imyhat listType = inner.type().asList();
    return new ReturnTypeGuarantee<Set<T>>() {
      @Override
      public Imyhat type() {
        return listType;
      }
    };
  }

  public static final ReturnTypeGuarantee<Boolean> BOOLEAN =
      new ReturnTypeGuarantee<Boolean>() {

        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  public static final ReturnTypeGuarantee<Instant> DATE =
      new ReturnTypeGuarantee<Instant>() {

        @Override
        public Imyhat type() {
          return Imyhat.DATE;
        }
      };
  public static final ReturnTypeGuarantee<Double> DOUBLE =
      new ReturnTypeGuarantee<Double>() {

        @Override
        public Imyhat type() {
          return Imyhat.FLOAT;
        }
      };
  public static final ReturnTypeGuarantee<Long> LONG =
      new ReturnTypeGuarantee<Long>() {

        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  public static final ReturnTypeGuarantee<Path> PATH =
      new ReturnTypeGuarantee<Path>() {

        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }
      };
  public static final ReturnTypeGuarantee<String> STRING =
      new ReturnTypeGuarantee<String>() {

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };

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

  ReturnTypeGuarantee() {}

  public abstract Imyhat type();
}
