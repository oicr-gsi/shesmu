package ca.on.oicr.gsi.shesmu.plugin.types;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

/**
 * Matches to make Java's type checking produce correct types in Shesmu
 *
 * <p>This class doesn't do anything per-se, it just allows Java type checker to produce a type that
 * is compatible with a Shesmu {@link Imyhat} without much work on the part of the author
 *
 * @param <T> the Java type being exported to Shesmu
 */
public interface TypeGuarantee<T> {
  TypeGuarantee<Boolean> BOOLEAN =
      new TypeGuarantee<Boolean>() {

        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  TypeGuarantee<Instant> DATE =
      new TypeGuarantee<Instant>() {

        @Override
        public Imyhat type() {
          return Imyhat.DATE;
        }
      };
  TypeGuarantee<Long> LONG =
      new TypeGuarantee<Long>() {

        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  TypeGuarantee<Path> PATH =
      new TypeGuarantee<Path>() {

        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }
      };
  TypeGuarantee<String> STRING =
      new TypeGuarantee<String>() {

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };

  public static <T> TypeGuarantee<Set<T>> list(TypeGuarantee<T> inner) {
    final Imyhat listType = inner.type().asList();
    return () -> listType;
  }

  Imyhat type();
}
