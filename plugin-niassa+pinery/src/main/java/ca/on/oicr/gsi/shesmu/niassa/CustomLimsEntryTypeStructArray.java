package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ToBytesConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The WDL output type for an array of structures with files and keys in them
 *
 * <p>The olive and the workflow don't need to guarantee anything about the order of the structs in
 * the array. Any field in the struct that is a string or integer is assumed to be part of a
 * composite key that is used for matching. Everything else is a file or equivalent and the olive
 * will provide the same information as if it was provisioned at the top level.
 */
public final class CustomLimsEntryTypeStructArray extends CustomLimsEntryType {
  private static final class StructArray extends CustomLimsEntry {
    private final List<StructEntry> entries;

    private StructArray(List<StructEntry> entries) {
      this.entries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StructArray that = (StructArray) o;
      return entries.equals(that.entries);
    }

    @Override
    Stream<Integer> fileSwids() {
      return entries.stream().flatMap(StructEntry::fileSwids);
    }

    @Override
    void generateUUID(Consumer<byte[]> digest) {
      for (final StructEntry entry : entries) {
        entry.generateUUID(digest);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(entries);
    }

    @Override
    Stream<? extends LimsKey> limsKeys() {
      return entries.stream().flatMap(StructEntry::limsKeys);
    }

    @Override
    boolean matches(Pattern query) {
      return entries.stream().anyMatch(entry -> entry.matches(query));
    }

    @Override
    JsonNode prepare(ToIntFunction<LimsKey> createIusLimsKey) {
      final ArrayNode array = NiassaServer.MAPPER.createArrayNode();
      for (final StructEntry entry : entries) {
        array.add(entry.prepare(createIusLimsKey));
      }
      return array;
    }

    @Override
    Stream<Pair<? extends LimsKey, String>> signatures() {
      return entries.stream().flatMap(StructEntry::signatures);
    }
  }

  private static final class StructEntry extends CustomLimsEntry {
    private final Map<String, CustomLimsEntry> children;
    private final Map<String, Pair<Imyhat, Object>> keys;

    private StructEntry(
        Map<String, CustomLimsEntry> children, Map<String, Pair<Imyhat, Object>> keys) {
      this.children = children;
      this.keys = keys;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StructEntry that = (StructEntry) o;
      return children.equals(that.children) && keys.equals(that.keys);
    }

    @Override
    Stream<Integer> fileSwids() {
      return children.values().stream().flatMap(CustomLimsEntry::fileSwids);
    }

    @Override
    void generateUUID(Consumer<byte[]> digest) {
      for (final Map.Entry<String, Pair<Imyhat, Object>> key : keys.entrySet()) {
        digest.accept(key.getKey().getBytes(StandardCharsets.UTF_8));
        digest.accept(new byte[] {0});
        key.getValue()
            .first()
            .accept(
                new ToBytesConverter() {
                  @Override
                  protected void add(byte[] bytes) {
                    digest.accept(bytes);
                  }
                },
                key.getValue().second());
        digest.accept(new byte[] {0});
      }
      for (final Map.Entry<String, CustomLimsEntry> child : children.entrySet()) {
        digest.accept(child.getKey().getBytes(StandardCharsets.UTF_8));
        digest.accept(new byte[] {0});
        child.getValue().generateUUID(digest);
        digest.accept(new byte[] {0});
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(children, keys);
    }

    @Override
    Stream<? extends LimsKey> limsKeys() {
      return children.values().stream().flatMap(CustomLimsEntry::limsKeys);
    }

    @Override
    boolean matches(Pattern query) {
      return keys.entrySet()
              .stream()
              .anyMatch(
                  e ->
                      query.matcher(e.getKey()).matches()
                          || query.matcher(e.getValue().toString()).matches())
          || children.values().stream().anyMatch(child -> child.matches(query));
    }

    @Override
    JsonNode prepare(ToIntFunction<LimsKey> createIusLimsKey) {
      final ObjectNode node = NiassaServer.MAPPER.createObjectNode();
      final ObjectNode keyNode = node.putObject("key");
      final ObjectNode outputNode = node.putObject("outputs");
      for (final Map.Entry<String, Pair<Imyhat, Object>> key : keys.entrySet()) {
        key.getValue()
            .first()
            .accept(new PackJsonObject(keyNode, key.getKey()), key.getValue().second());
      }
      for (final Map.Entry<String, CustomLimsEntry> child : children.entrySet()) {
        outputNode.set(child.getKey(), child.getValue().prepare(createIusLimsKey));
      }
      return node;
    }

    @Override
    Stream<Pair<? extends LimsKey, String>> signatures() {
      return children.values().stream().flatMap(CustomLimsEntry::signatures);
    }
  }

  private final Map<String, Either<Imyhat, CustomLimsEntryType>> fields;
  private final Imyhat.ObjectImyhat type;

  CustomLimsEntryTypeStructArray(List<Pair<String, Either<Imyhat, CustomLimsEntryType>>> inner) {
    fields = inner.stream().collect(Collectors.toMap(Pair::first, Pair::second));
    type =
        new Imyhat.ObjectImyhat(
            inner
                .stream()
                .map(
                    p ->
                        new Pair<>(
                            p.first(),
                            p.second().apply(Function.identity(), CustomLimsEntryType::type))));
  }

  @Override
  public CustomLimsEntry extract(Object value) {
    final List<StructEntry> entries = new ArrayList<>();
    for (final Object inner : (Set<?>) value) {
      final Tuple tuple = (Tuple) inner;
      final Map<String, Pair<Imyhat, Object>> keys = new TreeMap<>();
      final Map<String, CustomLimsEntry> children = new TreeMap<>();
      type.fields()
          .forEach(
              field -> {
                final Object fieldValue = tuple.get(field.getValue().second());
                fields
                    .get(field.getKey())
                    .accept(
                        identifierType ->
                            keys.put(field.getKey(), new Pair<>(identifierType, fieldValue)),
                        fieldProcessor ->
                            children.put(field.getKey(), fieldProcessor.extract(fieldValue)));
              });
      entries.add(new StructEntry(children, keys));
    }
    final AtomicReference<Comparator<StructEntry>> comparator = new AtomicReference<>((a, b) -> 0);
    fields.forEach(
        (key, field) ->
            field.accept(
                identifierType -> {
                  @SuppressWarnings("unchecked")
                  final Comparator<Object> innerComparator =
                      (Comparator<Object>) identifierType.comparator();
                  comparator.set(
                      comparator.get().thenComparing(x -> x.keys.get(key), innerComparator));
                },
                child -> {
                  // We don't sort on these because they keys define uniqueness
                }));

    entries.sort(comparator.get());
    return new StructArray(entries);
  }

  @Override
  public Imyhat type() {
    return type.asList();
  }
}
