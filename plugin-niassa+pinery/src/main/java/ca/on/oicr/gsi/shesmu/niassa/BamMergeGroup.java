package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BamMergeGroup {
  private final List<BamMergeEntry> entries;
  private final String groupName;

  public BamMergeGroup(String groupName, List<BamMergeEntry> entries) {
    this.groupName = groupName;
    this.entries = entries;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BamMergeGroup that = (BamMergeGroup) o;
    return entries.equals(that.entries) && groupName.equals(that.groupName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(entries, groupName);
  }

  public Stream<? extends LimsKey> limsKeys() {
    return entries.stream();
  }

  public boolean matches(Pattern query) {
    return query.matcher(groupName).matches()
        || entries.stream().anyMatch(entry -> entry.matches(query));
  }

  public BamMergeOutputInfo prepare(ToIntFunction<LimsKey> createIusLimsKey) {
    return new BamMergeOutputInfo(
        groupName,
        entries
            .stream()
            .map(entry -> new Pair<>(entry.fileName(), createIusLimsKey.applyAsInt(entry)))
            .collect(Collectors.toList()));
  }

  public boolean shouldHalp(Consumer<String> errorHandler) {
    if (entries.isEmpty()) {
      errorHandler.accept(String.format("Group %s has no entries.", groupName));
      return true;
    }
    return entries.stream().filter(e -> e.isStale(errorHandler)).count() > 0;
  }

  public Stream<Integer> swids() {
    return entries.stream().map(BamMergeEntry::swid);
  }
}
