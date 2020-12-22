package ca.on.oicr.gsi.shesmu.plugin.dumper;

/**
 * Write debugging values from an olive to an external file or database to aid in debugging.
 *
 * <p>These back the “Dump” olive clauses. Since Shesmu re-processes data, dumpers need to be aware
 * of this.
 */
public interface Dumper {

  /**
   * This is called after all the olives have produced their output and the round is complete.
   *
   * <p>This may be called multiple times in error conditions.
   */
  void stop();

  /** Write the provided values to the output. */
  void write(Object... values);
}
