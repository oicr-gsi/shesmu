package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds information from an olive about what LIMS associations there are from the output of a WDL
 * workflow run
 *
 * <p>These classes have corresponding {@link CustomLimsEntryType} class
 */
abstract class CustomLimsEntry {
  abstract Stream<Integer> fileSwids();

  abstract void generateUUID(Consumer<byte[]> digest);

  abstract Stream<? extends LimsKey> limsKeys();

  abstract boolean matches(Pattern query);

  abstract JsonNode prepare(ToIntFunction<LimsKey> createIusLimsKey);

  abstract Stream<Pair<? extends LimsKey, String>> signatures();
}
