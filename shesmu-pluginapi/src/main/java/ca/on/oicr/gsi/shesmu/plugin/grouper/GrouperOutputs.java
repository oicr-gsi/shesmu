package ca.on.oicr.gsi.shesmu.plugin.grouper;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A description of all the output provided by a grouper to an olive
 *
 * @param <I> the input row type; this is provided by Shesmu
 * @param <O> the output row type; this is provided by Shesmu
 * @param <C> the collector type
 */
public final class GrouperOutputs<I, O, C> {
  /**
   * A grouper that exports no variables to the olive.
   *
   * @param <I> the input row type; this is provided by Shesmu
   * @param <O> the output row type; this is provided by Shesmu
   */
  public static <I, O> GrouperOutputs<I, O, Supplier<BiConsumer<O, I>>> empty() {
    return new GrouperOutputs<>();
  }

  /**
   * A grouper that exports one variable to the olive.
   *
   * @param type the type of the variable that will be exported to the olive
   * @param <I> the input row type; this is provided by Shesmu
   * @param <O> the output row type; this is provided by Shesmu
   * @param <T> the type of the exported variable
   */
  public static <I, O, T> GrouperOutputs<I, O, Function<T, BiConsumer<O, I>>> of(
      GrouperOutput<I, T> type) {
    return new GrouperOutputs<>(type);
  }
  /**
   * A grouper that exports two variables to the olive.
   *
   * @param first the type of the first variable that will be exported to the olive
   * @param second the type of the second variable that will be exported to the olive
   * @param <I> the input row type; this is provided by Shesmu
   * @param <O> the output row type; this is provided by Shesmu
   * @param <T> the type of the first exported variable
   * @param <S> the type of the second exported variable
   */
  public static <I, O, T, S> GrouperOutputs<I, O, BiFunction<T, S, BiConsumer<O, I>>> of(
      GrouperOutput<I, T> first, GrouperOutput<I, S> second) {
    return new GrouperOutputs<>(first, second);
  }
  /**
   * A grouper that exports two variables to the olive.
   *
   * @param first the type of the first variable that will be exported to the olive
   * @param second the type of the second variable that will be exported to the olive
   * @param third the type of the third variable that will be exported to the olive
   * @param <I> the input row type; this is provided by Shesmu
   * @param <O> the output row type; this is provided by Shesmu
   * @param <T> the type of the first exported variable
   * @param <S> the type of the second exported variable
   * @param <U> the type of the third exported variable
   */
  public static <I, O, T, S, U> GrouperOutputs<I, O, TriGrouper<T, S, U, O, I>> of(
      GrouperOutput<I, T> first, GrouperOutput<I, S> second, GrouperOutput<I, U> third) {
    return new GrouperOutputs<>(first, second, third);
  }

  private final GrouperOutput<I, ?>[] arguments;

  @SafeVarargs
  private GrouperOutputs(GrouperOutput<I, ?>... arguments) {
    this.arguments = arguments;
  }

  public GrouperOutput<I, ?> get(int i) {
    return arguments[i];
  }

  public int size() {
    return arguments.length;
  }
}
