package ca.on.oicr.gsi.shesmu.plugin.grouper;

import ca.on.oicr.gsi.shesmu.plugin.types.GenericTypeGuarantee;
import java.util.function.Function;

/**
 * A description of parameter provided by an olive to a grouper
 *
 * @param <I> the type of the input row; this is provided by Shesmu
 * @param <T> the type of the parameter
 */
public abstract class GrouperParameter<I, T> {
  /**
   * Define a parameter which is the calculated for each row in the group
   *
   * @param name the default parameter name
   * @param type the type of the parameter
   * @param description user-facing documentation about what this parameter does
   * @param <I> the type of the input row; this is provided by Shesmu
   * @param <T> the type of the parameter
   */
  public static <I, T> GrouperParameter<I, Function<I, T>> dynamic(
      String name, GenericTypeGuarantee<T> type, String description) {
    return new GrouperParameter<I, Function<I, T>>() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public GrouperKind kind() {
        return GrouperKind.ROW_VALUE;
      }

      @Override
      public GenericTypeGuarantee<?> type() {
        return type;
      }

      @Override
      public Function<I, T> unpack(Object value) {
        @SuppressWarnings("unchecked")
        final Function<I, ?> function = (Function<I, ?>) value;
        return i -> type.unpack(function.apply(i));
      }
    };
  }

  /**
   * Define a parameter which is the same for all rows in the group
   *
   * @param name the default parameter name
   * @param type the type of the parameter
   * @param description user-facing documentation about what this parameter does
   * @param <I> the type of the input row; this is provided by Shesmu
   * @param <T> the type of the parameter
   */
  public static <I, T> GrouperParameter<I, T> fixed(
      String name, GenericTypeGuarantee<T> type, String description) {
    return new GrouperParameter<I, T>() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return description;
      }

      @Override
      public GrouperKind kind() {
        return GrouperKind.STATIC;
      }

      @Override
      public GenericTypeGuarantee<?> type() {
        return type;
      }

      @Override
      public T unpack(Object value) {
        return type.unpack(value);
      }
    };
  }

  public abstract String name();

  public abstract String description();

  public abstract GrouperKind kind();

  public abstract GenericTypeGuarantee<?> type();

  public abstract T unpack(Object value);
}
