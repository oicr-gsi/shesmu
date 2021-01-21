package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class SignedFilesInputLimsCollection implements InputLimsCollection {
  final List<SignedFilesInputFile> filesInputFileInformation;

  public SignedFilesInputLimsCollection(List<SignedFilesInputFile> value) {
    filesInputFileInformation = value;
    filesInputFileInformation.sort(
        Comparator.comparing(SignedFilesInputFile::swid)
            .thenComparing(SignedFilesInputFile::getProvider)
            .thenComparing(SignedFilesInputFile::getId)
            .thenComparing(SignedFilesInputFile::getVersion));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SignedFilesInputLimsCollection that = (SignedFilesInputLimsCollection) o;
    return filesInputFileInformation.equals(that.filesInputFileInformation);
  }

  @Override
  public Stream<Integer> fileSwids() {
    return filesInputFileInformation.stream().map(SignedFilesInputFile::swid);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    for (final SignedFilesInputFile fileInfo : filesInputFileInformation) {
      fileInfo.generateUUID(digest);
      digest.accept(new byte[] {0});
    }
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
    for (final SignedFilesInputFile filesInputFile : filesInputFileInformation) {
      createIusLimsKey.applyAsInt(filesInputFile);
    }
  }

  @Override
  public boolean shouldZombie(Consumer<String> errorHandler) {
    if (filesInputFileInformation.isEmpty()) {
      errorHandler.accept("No files attached");
      return true;
    }
    return filesInputFileInformation.stream().filter(f -> f.isStale(errorHandler)).count() > 0;
  }

  @Override
  public Stream<Pair<? extends LimsKey, String>> signatures() {
    return filesInputFileInformation.stream().map(SignedFilesInputFile::signature);
  }
}
