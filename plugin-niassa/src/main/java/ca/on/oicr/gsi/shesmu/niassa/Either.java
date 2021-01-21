package ca.on.oicr.gsi.shesmu.niassa;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A value that holds one of two mutually exclusive values
 *
 * @param <A> one of the possible types
 * @param <B> the other possible type
 */
public abstract class Either<A, B> {

  /**
   * Create a new either filling in the first value as provided
   *
   * @param value the value to use
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   */
  public static <A, B> Either<A, B> first(A value) {
    return new Either<A, B>() {
      @Override
      public void accept(Consumer<? super A> aConsumer, Consumer<? super B> bConsumer) {
        aConsumer.accept(value);
      }

      @Override
      public <T> T apply(
          Function<? super A, ? extends T> aFunction, Function<? super B, ? extends T> bFunction) {
        return aFunction.apply(value);
      }
    };
  }

  /**
   * Create a new either filling in the second value as provided
   *
   * @param value the value to use
   * @param <A> the type of the first value
   * @param <B> the type of the second value
   */
  public static <A, B> Either<A, B> second(B value) {
    return new Either<A, B>() {
      @Override
      public void accept(Consumer<? super A> aConsumer, Consumer<? super B> bConsumer) {
        bConsumer.accept(value);
      }

      @Override
      public <T> T apply(
          Function<? super A, ? extends T> aFunction, Function<? super B, ? extends T> bFunction) {
        return bFunction.apply(value);
      }
    };
  }

  /**
   * Consume the value stored
   *
   * @param aConsumer the consumer to use if the value has the first type
   * @param bConsumer the consumer to use if the value has the second type
   */
  public abstract void accept(Consumer<? super A> aConsumer, Consumer<? super B> bConsumer);

  /**
   * Convert the value stored to an output
   *
   * @param aFunction the conversion to use if the value has the first type
   * @param bFunction the conversion to use if the value has the second type
   * @param <T> the result type
   */
  public abstract <T> T apply(
      Function<? super A, ? extends T> aFunction, Function<? super B, ? extends T> bFunction);
}
