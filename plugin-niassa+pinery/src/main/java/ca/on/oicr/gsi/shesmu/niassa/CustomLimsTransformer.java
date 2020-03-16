package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.BiConsumer;

/**
 * An object to consume the olive's output
 *
 * <p>This can either be a {@link CustomLimsEntryType} to read data or {@link PackWdlOutputs} to
 * collapse the nested objects
 */
public interface CustomLimsTransformer {
  /**
   * Collect any data from the olive and emit any discovered data
   *
   * @param type the type of the data from the olive
   * @param value the data from the olive
   * @param output the handler for the output
   */
  void write(Imyhat type, Object value, BiConsumer<String, CustomLimsEntry> output);
}
