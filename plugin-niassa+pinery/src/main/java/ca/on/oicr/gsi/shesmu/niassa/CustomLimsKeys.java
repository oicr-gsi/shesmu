package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holds all of the LIMS data for a custom workflow run
 *
 * <p>This knows how to keep all the LIMS key information in a usable format and compare it for
 * equality. It's really just a shell for the "bits" inside of it that know to associate individual
 * outputs from the workflow run with the metadata
 */
final class CustomLimsKeys implements InputLimsCollection {
  private final Map<String, CustomLimsEntry> entries;

  CustomLimsKeys(List<Pair<String, CustomLimsEntry>> entries) {
    this.entries =
        entries
            .stream()
            .collect(
                Collectors.toMap(
                    Pair::first,
                    Pair::second,
                    (a, b) -> {
                      throw new IllegalStateException("Duplicate keys for WDL outputs");
                    },
                    TreeMap::new));
  }

  @Override
  public Stream<Integer> fileSwids() {
    return entries.values().stream().flatMap(CustomLimsEntry::fileSwids);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    for (final Map.Entry<String, CustomLimsEntry> entry : entries.entrySet()) {
      digest.accept(entry.getKey().getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      entry.getValue().generateUUID(digest);
    }
  }

  @Override
  public Stream<? extends LimsKey> limsKeys() {
    return entries.values().stream().flatMap(CustomLimsEntry::limsKeys);
  }

  @Override
  public boolean matches(Pattern query) {
    return entries.values().stream().anyMatch(e -> e.matches(query));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CustomLimsKeys that = (CustomLimsKeys) o;
    return Objects.equals(entries, that.entries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entries);
  }

  @Override
  public void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini) {
    final ObjectNode outputs = NiassaServer.MAPPER.createObjectNode();
    for (final Map.Entry<String, CustomLimsEntry> entry : entries.entrySet()) {
      outputs.set(entry.getKey(), entry.getValue().prepare(createIusLimsKey));
    }
    try {
      ini.setProperty("wdl_outputs", NiassaServer.MAPPER.writeValueAsString(outputs));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean shouldZombie(Consumer<String> errorHandler) {
    boolean shouldZombie = false;
    for (final CustomLimsEntry child : entries.values()) {
      shouldZombie = child.shouldZombie(errorHandler) || shouldZombie;
    }
    return shouldZombie;
  }

  @Override
  public Stream<Pair<? extends LimsKey, String>> signatures() {
    return entries.values().stream().flatMap(CustomLimsEntry::signatures);
  }
}
