package ca.on.oicr.gsi.shesmu.plugin.types;

import ca.on.oicr.gsi.Pair;
import java.util.Map;
import java.util.Set;

/**
 * Create a return type that includes type variables
 *
 * @param <T> the Java type being imported to Shesmu
 */
public abstract class GenericReturnTypeGuarantee<T> {
  /**
   * Create a list that includes type variables
   *
   * @param inner the type of the contents of the list
   * @param <T> the Java type of the contents of the list
   */
  public static <T> GenericReturnTypeGuarantee<Set<T>> list(GenericReturnTypeGuarantee<T> inner) {
    return new GenericReturnTypeGuarantee<>() {

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
    };
  }

  /**
   * Create a type variable
   *
   * @param clazz the upper bound for the type variable
   * @param id a unique name for the variable; if it is not unique in the context of inference, bad
   *     things will happen
   * @param <T> the Java type for the Shesmu type being transited through Java
   */
  public static <T> Pair<GenericTypeGuarantee<T>, GenericReturnTypeGuarantee<T>> variable(
      Class<? extends T> clazz, String id) {
    // This is a situation that require multiple inheritance, so what we do is produce two values
    // and then have one delegate to the other
    final var type =
        new GenericTypeGuarantee<T>() {
          @Override
          public <R> R apply(GenericTransformer<R> transformer) {
            return transformer.generic(id);
          }

          @Override
          public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
            if (variables.containsKey(id)) {
              return variables.get(id).isSame(reference);
            }
            variables.put(id, reference);
            return true;
          }

          @Override
          public Imyhat render(Map<String, Imyhat> variables) {
            return variables.getOrDefault(id, Imyhat.BAD);
          }

          @Override
          public String toString(Map<String, Imyhat> typeVariables) {
            final var imyhat = typeVariables.get(id);
            return imyhat == null ? id : imyhat.name();
          }

          @Override
          public T unpack(Object object) {
            return clazz.cast(object);
          }
        };
    return new Pair<>(
        type,
        new GenericReturnTypeGuarantee<>() {
          @Override
          public boolean check(Map<String, Imyhat> variables, Imyhat reference) {
            return type.check(variables, reference);
          }

          @Override
          public Imyhat render(Map<String, Imyhat> variables) {
            return type.render(variables);
          }

          @Override
          public String toString(Map<String, Imyhat> typeVariables) {
            return type.toString(typeVariables);
          }
        });
  }

  GenericReturnTypeGuarantee() {}

  public abstract boolean check(Map<String, Imyhat> variables, Imyhat reference);

  public abstract Imyhat render(Map<String, Imyhat> variables);

  public abstract String toString(Map<String, Imyhat> typeVariables);
}
