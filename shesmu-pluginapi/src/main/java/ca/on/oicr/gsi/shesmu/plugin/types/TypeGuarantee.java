package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
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
  TypeGuarantee<Boolean> BOOLEAN = () -> Imyhat.BOOLEAN;
  TypeGuarantee<Instant> DATE = () -> Imyhat.DATE;
  TypeGuarantee<Long> LONG = () -> Imyhat.INTEGER;
  TypeGuarantee<Path> PATH = () -> Imyhat.PATH;
  TypeGuarantee<String> STRING = () -> Imyhat.STRING;

  static <T> TypeGuarantee<Set<T>> list(TypeGuarantee<T> inner) {
    final Imyhat listType = inner.type().asList();
    return () -> listType;
  }

  static TypeGuarantee<Tuple> tuple(Imyhat... elements) {
    final Imyhat tupleType = Imyhat.tuple(elements);
    return () -> tupleType;
  }

  Imyhat type();
}
