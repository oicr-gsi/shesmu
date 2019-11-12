package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Some ball of data obtained from the olive that holds information that can be converted into LIMS
 * keys and file SWIDs that can be registered and stuffed in an INI in whatever way a workflow
 * expects.
 */
public interface InputLimsCollection {
  /** List all the input file SWIDs that should be associated with this workflow run */
  Stream<Integer> fileSwids();

  /**
   * Generate a UUID compatible with {@link
   * ca.on.oicr.gsi.shesmu.plugin.action.Action#generateUUID(Consumer)}
   */
  void generateUUID(Consumer<byte[]> digest);

  /** List all the LIMS keys that should be associated with this workflow run */
  Stream<? extends LimsKey> limsKeys();

  /**
   * Get a list of all signatures associated with their LIMS keys. There can be at most one unique
   * signature for each LIMS key or the workflow may get wedged.
   *
   * <p>The SHA1 signature should be used in the olive, but we've got no sensible way of enforcing
   * that on this end. Technically, it doesn't matter as long as it fits in Niassa's annotation
   * value field.
   */
  Stream<Pair<? extends LimsKey, String>> signatures();

  /** Check if anything matches text search query */
  boolean matches(Pattern query);

  /**
   * Register all LIMS keys and update the INI as necessary
   *
   * @param createIusLimsKey function to generate an IUS SWID for a LIMS key
   * @param ini the output INI
   */
  void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini);

  /**
   * Check whether the input is broken or stale in such a way that the action should go into {@link
   * ca.on.oicr.gsi.shesmu.plugin.action.ActionState#HALP} and not run.
   */
  boolean shouldHalp(Consumer<String> errorHandler);
}
