package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Bcl2FastqInputLimsCollection implements InputLimsCollection {
  private final List<Bcl2FastqLaneEntry> lanes;

  public Bcl2FastqInputLimsCollection(List<Bcl2FastqLaneEntry> lanes) {
    this.lanes = lanes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Bcl2FastqInputLimsCollection that = (Bcl2FastqInputLimsCollection) o;
    return lanes.equals(that.lanes);
  }

  @Override
  public Stream<Integer> fileSwids() {
    return Stream.empty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(lanes);
  }

  @Override
  public Stream<? extends LimsKey> limsKeys() {
    return lanes.stream().flatMap(Bcl2FastqLaneEntry::limsKeys);
  }

  @Override
  public boolean matches(Pattern query) {
    return lanes.stream().anyMatch(lane -> lane.matches(query));
  }

  @Override
  public void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini) {
    ini.setProperty(
        "lanes",
        lanes
            .stream()
            .map(lane -> lane.prepare(createIusLimsKey))
            .collect(Collectors.joining("|")));
  }

  @Override
  public boolean shouldHalp(Consumer<String> errorHandler) {
    if (lanes.isEmpty()) {
      errorHandler.accept("No lanes.");
      return true;
    }
    return false;
  }
}
