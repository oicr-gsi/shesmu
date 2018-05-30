package ca.on.oicr.gsi.shesmu;

/**
 * Write debugging values from an olive to an external file or database to aid
 * in debugging.
 * 
 * These back the “Dump” olive clauses. Since Shesmu re-processes data, dumpers
 * need to be aware of this.
 */
public interface Dumper {

	/**
	 * This is called before data is written for every round of olive processing.
	 * 
	 * This should truncate and previous output, if appropriate.
	 */
	public void start();

	/**
	 * This is called after all the olives have produced their output and the round
	 * is complete.
	 */
	public void stop();

	/**
	 * Write the provided values to the output.
	 */
	public void write(Object[] values);
}
