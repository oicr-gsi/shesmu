package ca.on.oicr.gsi.shesmu.plugin.refill;

import java.util.stream.Stream;

/**
 * A database that can consume all of an olive's output and store it in an appropriate way.
 *
 * @param <T> the type of the input records; all implementors of this class must take exactly one
 *     type variable, which the Shesmu olive will provide
 */
public abstract class Refiller<T> {
  /**
   * Consume the output of the olive
   *
   * @param items the rows produced by the olive
   */
  public abstract void consume(Stream<T> items);
}
