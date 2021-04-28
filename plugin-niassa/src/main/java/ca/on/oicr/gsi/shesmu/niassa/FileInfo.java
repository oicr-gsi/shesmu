package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class FileInfo implements Comparable<FileInfo>, Iterable<Pair<String, String>> {

  private final int accession;
  private final Set<Pair<String, String>> limsKeys;
  private final String md5sum;
  private final String metatype;
  private final String path;
  private final long size;

  public FileInfo(AnalysisProvenance ap) {
    this.accession = ap.getFileId();
    this.md5sum = ap.getFileMd5sum();
    this.metatype = ap.getFileMetaType();
    this.path = ap.getFilePath();
    this.size = ap.getFileSize() == null ? 0 : Long.parseUnsignedLong(ap.getFileSize());
    limsKeys =
        ap.getIusLimsKeys().stream()
            .filter(k -> k.getLimsKey() != null)
            .map(k -> new Pair<>(k.getLimsKey().getProvider(), k.getLimsKey().getId()))
            .collect(Collectors.toSet());
  }

  @Override
  public int compareTo(FileInfo fileInfo) {
    return Integer.compare(accession, fileInfo.accession);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FileInfo fileInfo = (FileInfo) o;
    return accession == fileInfo.accession
        && metatype.equals(fileInfo.metatype)
        && path.equals(fileInfo.path);
  }

  public int getAccession() {
    return accession;
  }

  public String getPath() {
    return path;
  }

  @Override
  public int hashCode() {
    return Objects.hash(accession, metatype, path);
  }

  @Override
  public Iterator<Pair<String, String>> iterator() {
    return limsKeys.iterator();
  }

  public void toJson(ArrayNode array) {
    final ObjectNode output = array.addObject();
    output.put("accession", accession);
    output.put("md5sum", md5sum);
    output.put("metatype", metatype);
    output.put("path", path);
    output.put("size", size);
  }
}
