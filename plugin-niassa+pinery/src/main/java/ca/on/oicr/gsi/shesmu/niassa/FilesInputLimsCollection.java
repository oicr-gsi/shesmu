package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class FilesInputLimsCollection implements InputLimsCollection {
  final List<FilesInputFile> filesInputFileInformation;

  public FilesInputLimsCollection(List<FilesInputFile> value) {
    filesInputFileInformation = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FilesInputLimsCollection that = (FilesInputLimsCollection) o;
    return filesInputFileInformation.equals(that.filesInputFileInformation);
  }

  @Override
  public Stream<Integer> fileSwids() {
    return filesInputFileInformation.stream().map(FilesInputFile::swid);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filesInputFileInformation);
  }

  @Override
  public Stream<? extends LimsKey> limsKeys() {
    return filesInputFileInformation.stream();
  }

  @Override
  public boolean matches(Pattern query) {
    return filesInputFileInformation.stream().anyMatch(info -> info.matches(query));
  }

  @Override
  public void prepare(ToIntFunction<LimsKey> createIusLimsKey, Properties ini) {
    for (FilesInputFile filesInputFile : filesInputFileInformation) {
      createIusLimsKey.applyAsInt(filesInputFile);
    }
  }

  @Override
  public boolean shouldHalp() {
    return filesInputFileInformation.isEmpty()
        || filesInputFileInformation.stream().anyMatch(FilesInputFile::isStale);
  }
}
