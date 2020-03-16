package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

/**
 * This is a piece of information that the workflow will expect from the olive
 *
 * <p>It only describes the format of this data, not the data itself. For that, a corresponding
 * {@link CustomLimsEntry} class exists.
 */
public abstract class CustomLimsEntryType {

  /**
   * Bind this transformation to a particular WDL property name
   *
   * <p>This only happens for the top-level entity types
   *
   * @param name the WDL output name, of the form <tt>workflow.var</tt>
   */
  public final CustomLimsTransformer bind(String name) {
    return (type, value, output) -> output.accept(name, extract(value));
  }

  /**
   * Take the data from the olive and create an entry that {@link CustomLimsKeys} can use to track
   * this data and add it to the INI
   *
   * @param value the data from the olive
   */
  public abstract CustomLimsEntry extract(Object value);

  /** The type of data the olive must provide. */
  public abstract Imyhat type();
}
