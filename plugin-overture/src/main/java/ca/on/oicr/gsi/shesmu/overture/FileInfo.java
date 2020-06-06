package ca.on.oicr.gsi.shesmu.overture;

import ca.on.gsi.shesm.overture.song.model.FileEntity;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

public final class FileInfo {
  public static final Comparator<FileInfo> COMPARATOR =
      Comparator.comparing(FileInfo::access)
          .thenComparing(FileInfo::dataType)
          .thenComparing(FileInfo::type)
          .thenComparing(f -> f.file().getFileName().toString());
  // This comparator needs to match the other one to allow zippering
  public static final Comparator<FileEntity> COMPARATOR_ENTITY =
      Comparator.comparing(FileEntity::getFileAccess)
          .thenComparing(FileEntity::getDataType)
          .thenComparing(FileEntity::getFileType)
          .thenComparing(FileEntity::getFileName);

  public String access() {
    return access;
  }

  public String dataType() {
    return dataType;
  }

  public Path file() {
    return file;
  }

  public JsonNode info() {
    return info;
  }

  public String md5() {
    return md5;
  }

  public long size() {
    return size;
  }

  public String type() {
    return type;
  }

  private final String access;
  private final String dataType;
  private final Path file;
  private final JsonNode info;
  private final String md5;
  private final long size;
  private final String type;

  public FileInfo(Tuple input) {
    access = (String) input.get(0);
    dataType = (String) input.get(1);
    file = (Path) input.get(2);
    info = (JsonNode) input.get(3);
    md5 = (String) input.get(4);
    size = (long) input.get(5);
    type = (String) input.get(6);
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
    return size == fileInfo.size
        && access.equals(fileInfo.access)
        && dataType.equals(fileInfo.dataType)
        && file.equals(fileInfo.file)
        && info.equals(fileInfo.info)
        && md5.equals(fileInfo.md5)
        && type.equals(fileInfo.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(access, dataType, file, info, md5, size, type);
  }

  public boolean matches(FileEntity fileEntity) {
    return fileEntity.getDataType().equals(dataType)
        && fileEntity.getFileAccess().equals(access)
        && fileEntity.getFileMd5sum().equals(md5)
        && fileEntity.getFileName().equals(file.getFileName().toString())
        && fileEntity.getFileSize() == size
        && fileEntity.getFileType().equals(type);
  }
}
