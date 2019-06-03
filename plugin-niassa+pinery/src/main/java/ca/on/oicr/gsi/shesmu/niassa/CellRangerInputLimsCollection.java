package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.*;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class CellRangerInputLimsCollection implements InputLimsCollection {
  final List<CellRangerIUSEntry> limsKeys;

  public CellRangerInputLimsCollection(List<CellRangerIUSEntry> value) {
    limsKeys = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CellRangerInputLimsCollection that = (CellRangerInputLimsCollection) o;
    return limsKeys.equals(that.limsKeys);
  }

  @Override
  public Stream<Integer> fileSwids() {
    return Stream.empty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(limsKeys);
  }

  @Override
  public Stream<? extends LimsKey> limsKeys() {
    return limsKeys.stream();
  }

  @Override
  public boolean matches(Pattern query) {
    return limsKeys.stream().anyMatch(key -> key.matches(query));
  }

  @Override
  public void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini) {
    final List<String> lanes = new ArrayList<>();
    for (CellRangerIUSEntry limsKey : limsKeys) {
      lanes.add(limsKey.asLaneString(createIusLimsKey.applyAsInt(limsKey)));
    }
    ini.setProperty("lanes", String.join("+", lanes));
  }

  @Override
  public boolean shouldHalp() {
    return limsKeys.isEmpty();
  }
}
