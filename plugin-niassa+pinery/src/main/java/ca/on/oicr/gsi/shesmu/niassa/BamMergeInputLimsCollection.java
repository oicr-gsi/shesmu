package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BamMergeInputLimsCollection implements InputLimsCollection {
  private final List<BamMergeGroup> groups;

  public BamMergeInputLimsCollection(List<BamMergeGroup> input) {
    groups = input;
    groups.sort(Comparator.comparing(BamMergeGroup::name));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BamMergeInputLimsCollection that = (BamMergeInputLimsCollection) o;
    return groups.equals(that.groups);
  }

  @Override
  public int hashCode() {
    return Objects.hash(groups);
  }

  @Override
  public Stream<Integer> fileSwids() {
    return groups.stream().flatMap(BamMergeGroup::swids);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    for (final BamMergeGroup group : groups) {
      group.generateUUID(digest);
    }
  }

  @Override
  public Stream<? extends LimsKey> limsKeys() {
    return groups.stream().flatMap(BamMergeGroup::limsKeys);
  }

  @Override
  public boolean matches(Pattern query) {
    return groups.stream().anyMatch(group -> group.matches(query));
  }

  @Override
  public void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini) {
    final List<BamMergeOutputInfo> iniParams =
        groups.stream().map(group -> group.prepare(createIusLimsKey)).collect(Collectors.toList());
    ini.put(
        "output_identifiers",
        iniParams.stream().map(BamMergeOutputInfo::outputName).collect(Collectors.joining(";")));
    ini.put(
        "input_files",
        iniParams.stream().map(BamMergeOutputInfo::files).collect(Collectors.joining(";")));
    if (iniParams.size() > 1) {
      // when there is only one group, output files should use the IUS-LimsKeys associated with the
      // workflow run
      // where there is more than one group, the workflow needs to assoicate the output files with
      // the approriate IUS-LimsKeys
      // when in test/dry-run mode, the iusLimsKeysStringByGroup will be empty
      ini.put(
          "output_ius_lims_keys",
          iniParams
              .stream()
              .map(BamMergeOutputInfo::iusLimsKeySwids)
              .collect(Collectors.joining(";")));
    }
  }

  @Override
  public boolean shouldHalp(Consumer<String> errorHandler) {
    if (groups.isEmpty()) {
      errorHandler.accept("No input groups.");
      return true;
    }
    return groups.stream().filter(g -> g.shouldHalp(errorHandler)).count() > 0;
  }
}
