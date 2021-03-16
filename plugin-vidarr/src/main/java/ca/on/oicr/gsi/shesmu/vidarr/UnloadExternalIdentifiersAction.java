package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.vidarr.UnloadFilter;
import ca.on.oicr.gsi.vidarr.UnloadTextSelector;
import ca.on.oicr.gsi.vidarr.api.UnloadFilterExternalId;
import ca.on.oicr.gsi.vidarr.api.UnloadFilterOr;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UnloadExternalIdentifiersAction extends BaseUnloadAction {
  private Map<String, List<String>> externalIds = Map.of();

  public UnloadExternalIdentifiersAction(Supplier<VidarrPlugin> owner) {
    super("external-identifiers", owner);
  }

  @Override
  protected void addFilterForJson(ObjectNode node) {
    node.putPOJO("externalIds", externalIds);
  }

  @Override
  protected UnloadFilter createFilter() {
    if (externalIds.isEmpty()) {
      return null;
    }
    final var filter = new UnloadFilterOr();
    filter.setFilters(
        externalIds.entrySet().stream()
            .map(
                entry -> {
                  final var providerFilter = new UnloadFilterExternalId();
                  providerFilter.setProvider(entry.getKey());
                  providerFilter.setId(UnloadTextSelector.of(entry.getValue()));
                  return providerFilter;
                })
            .collect(Collectors.toList()));
    return filter;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UnloadExternalIdentifiersAction that = (UnloadExternalIdentifiersAction) o;
    return externalIds.equals(that.externalIds);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    for (final var entry : externalIds.entrySet()) {
      digest.accept(new byte[] {0});
      digest.accept(new byte[] {0});
      digest.accept(entry.getKey().getBytes(StandardCharsets.UTF_8));
      for (final var id : entry.getValue()) {
        digest.accept(id.getBytes(StandardCharsets.UTF_8));
        digest.accept(new byte[] {0});
      }
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(externalIds);
  }

  @Override
  public boolean search(Pattern query) {
    return externalIds.entrySet().stream()
        .flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), entry.getValue().stream()))
        .anyMatch(query.asPredicate());
  }

  @ActionParameter(name = "external_identifiers", type = "ao2id$sprovider$s")
  public void setExternalIds(Set<Tuple> externalIds) {
    this.externalIds =
        externalIds.stream()
            .collect(
                Collectors.groupingBy(
                    tuple -> (String) tuple.get(1),
                    Collectors.mapping(tuple -> (String) tuple.get(0), Collectors.toList())));
  }
}
