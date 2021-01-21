package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The WDL output type for a file, array of files, annotated file, or annotated array of files
 *
 * <p>While all these are different output types from the WDL workflow, they require the same kind
 * of input data from the olive, so they are treated the same
 */
public final class CustomLimsEntryTypeTerminal extends CustomLimsEntryType {
  private static final class TerminalEntry extends CustomLimsEntry {
    private final Map<String, String> attributes;
    private final Set<Pair<Integer, Boolean>> fileSwids;
    private final Map<SimpleLimsKey, String> limsKeys;
    private final Optional<String> metaType;

    private TerminalEntry(
        Map<String, String> attributes,
        Set<Pair<Integer, Boolean>> fileSwids,
        Map<SimpleLimsKey, String> limsKeys,
        Optional<String> metaType) {
      this.attributes = attributes;
      this.fileSwids = fileSwids;
      this.limsKeys = limsKeys;
      this.metaType = metaType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TerminalEntry that = (TerminalEntry) o;
      return attributes.equals(that.attributes)
          && limsKeys.equals(that.limsKeys)
          && metaType.equals(that.metaType);
    }

    @Override
    Stream<Integer> fileSwids() {
      return fileSwids.stream().map(Pair::first);
    }

    @Override
    void generateUUID(Consumer<byte[]> digest) {
      for (final Map.Entry<String, String> attribute : attributes.entrySet()) {
        digest.accept(new byte[] {0});
        digest.accept(attribute.getKey().getBytes(StandardCharsets.UTF_8));
        digest.accept(new byte[] {0});
        digest.accept(attribute.getValue().getBytes(StandardCharsets.UTF_8));
      }
      for (final Map.Entry<SimpleLimsKey, String> limsKey : limsKeys.entrySet()) {
        digest.accept(limsKey.getKey().getId().getBytes(StandardCharsets.UTF_8));
        digest.accept(limsKey.getKey().getProvider().getBytes(StandardCharsets.UTF_8));
        digest.accept(Utils.toBytes(limsKey.getKey().getLastModified().toEpochSecond()));
        digest.accept(limsKey.getKey().getVersion().getBytes(StandardCharsets.UTF_8));
        digest.accept(limsKey.getValue().getBytes(StandardCharsets.UTF_8));
        digest.accept(new byte[] {0});
      }
      digest.accept(metaType.orElse("").getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int hashCode() {
      return Objects.hash(attributes, limsKeys, metaType);
    }

    @Override
    Stream<? extends LimsKey> limsKeys() {
      return limsKeys.keySet().stream();
    }

    @Override
    boolean matches(Pattern query) {
      return attributes
              .entrySet()
              .stream()
              .anyMatch(
                  e -> query.matcher(e.getKey()).matches() || query.matcher(e.getValue()).matches())
          || limsKeys
              .entrySet()
              .stream()
              .anyMatch(
                  e ->
                      query.matcher(e.getKey().getId()).matches()
                          || query.matcher(e.getKey().getProvider()).matches()
                          || query.matcher(e.getKey().getVersion()).matches()
                          || query.matcher(e.getValue()).matches())
          || metaType.map(mt -> query.matcher(mt).matches()).orElse(false);
    }

    @Override
    JsonNode prepare(ToIntFunction<LimsKey> createIusLimsKey) {
      final ObjectNode node = NiassaServer.MAPPER.createObjectNode();
      limsKeys.keySet().stream().mapToInt(createIusLimsKey).forEach(node.putArray("limsKeys")::add);
      final ObjectNode attributeNode = node.putObject("attributes");
      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        attributeNode.put(attribute.getKey(), attribute.getValue());
      }
      node.put("metatype", metaType.orElse(null));
      return node;
    }

    @Override
    public boolean shouldZombie(Consumer<String> errorConsumer) {
      boolean shouldZombie = false;
      for (final Pair<Integer, Boolean> fileSwid : fileSwids) {
        if (fileSwid.second()) {
          shouldZombie = true;
          errorConsumer.accept(
              String.format(
                  "Input file %d for %s is marked as stale. Fix provenance and purge this action.",
                  fileSwid.first(),
                  limsKeys
                      .keySet()
                      .stream()
                      .map(LimsKey::getId)
                      .collect(Collectors.joining(" or "))));
        }
      }
      return shouldZombie;
    }

    @Override
    Stream<Pair<? extends LimsKey, String>> signatures() {
      return limsKeys.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue()));
    }
  }

  private static final Comparator<Pair<Integer, Boolean>> SWID_STALE_COMPARATOR =
      Comparator.<Pair<Integer, Boolean>, Integer>comparing(Pair::first)
          .thenComparing(Pair::second);

  private static final Imyhat TYPE =
      new Imyhat.ObjectImyhat(
          Stream.of(
              new Pair<>("attributes", Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList()),
              new Pair<>("files", Imyhat.tuple(Imyhat.STRING, Imyhat.BOOLEAN).asList()),
              new Pair<>(
                  "lims_keys",
                  new Imyhat.ObjectImyhat(
                          Stream.of(
                              new Pair<>("id", Imyhat.STRING),
                              new Pair<>("provider", Imyhat.STRING),
                              new Pair<>("signature", Imyhat.STRING),
                              new Pair<>("time", Imyhat.DATE),
                              new Pair<>("version", Imyhat.STRING)))
                      .asList()),
              new Pair<>("metatype", Imyhat.STRING.asOptional())));

  CustomLimsEntryTypeTerminal() {}

  @Override
  public CustomLimsEntry extract(Object value) {
    final Tuple tuple = (Tuple) value;
    final Map<String, String> attributes = new TreeMap<>();
    for (final Object attribute : (Set<?>) tuple.get(0)) {
      final Tuple attributeTuple = (Tuple) attribute;
      attributes.put((String) attributeTuple.get(0), (String) attributeTuple.get(1));
    }
    final Set<Pair<Integer, Boolean>> fileSwids =
        ((Set<?>) tuple.get(1))
            .stream()
            .map(
                o ->
                    new Pair<>(
                        Integer.parseInt(((Tuple) o).get(0).toString()),
                        (Boolean) ((Tuple) o).get(1)))
            .collect(Collectors.toCollection(() -> new TreeSet<>(SWID_STALE_COMPARATOR)));
    final Map<SimpleLimsKey, String> limsKeys = new TreeMap<>(WorkflowAction.LIMS_KEY_COMPARATOR);
    for (final Object limsKey : (Set<?>) tuple.get(2)) {
      final Tuple limsKeyTuple = (Tuple) limsKey;
      final String id = (String) limsKeyTuple.get(0);
      final String provider = (String) limsKeyTuple.get(1);
      final String signature = (String) limsKeyTuple.get(2);
      final Instant time = (Instant) limsKeyTuple.get(3);
      final String version = (String) limsKeyTuple.get(4);
      limsKeys.put(new SimpleLimsKey(id, provider, time, version), signature);
    }
    final Optional<String> metaType = ((Optional<?>) tuple.get(3)).map(String.class::cast);
    return new TerminalEntry(attributes, fileSwids, limsKeys, metaType);
  }

  @Override
  public final Imyhat type() {
    return TYPE;
  }
}
