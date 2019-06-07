package ca.on.oicr.gsi.shesmu.plugin.grouper;

import ca.on.oicr.gsi.shesmu.plugin.types.GenericReturnTypeGuarantee;
import java.util.function.Function;

/**
 * A description of an output variable provided by a grouper to an olive
 *
 * @param <I> the input row type; this is provided by Shesmu
 * @param <T> the type of the exported value
 */
public abstract class GrouperOutput<I, T> {
  /**
   * A grouper that exports two variables to the olive.
   *
   * @param name the default name to use in the olive
   * @param type the type of the variable that will be exported to the olive
   * @param description help text that is shown to the user
   * @param <I> the input row type; this is provided by Shesmu
   * @param <T> the type of the exported variable
   */
  public static <I, T> GrouperOutput<I, Function<I, T>> dynamic(
      String name, GenericReturnTypeGuarantee<T> type, String description) {
    return new GrouperOutput<I, Function<I, T>>() {
      @Override
      public String defaultName() {
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
      public GenericReturnTypeGuarantee<?> type() {
        return type;
      }
    };
  }

  /**
   * An exported variable that is fixed across all member of the group
   *
   * @param name the default name to use in the olive
   * @param type the type of the variable that will be exported to the olive
   * @param description help text that is shown to the user
   * @param <I> the input row type; this is provided by Shesmu
   * @param <T> the type of the exported variable
   */
  public static <I, T> GrouperOutput<I, T> fixed(
      String name, GenericReturnTypeGuarantee<T> type, String description) {
    return new GrouperOutput<I, T>() {
      @Override
      public String defaultName() {
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
      public GenericReturnTypeGuarantee<?> type() {
        return type;
      }
    };
  }

  public abstract String defaultName();

  public abstract String description();

  public abstract GrouperKind kind();

  public abstract GenericReturnTypeGuarantee<?> type();
}
