package ca.on.oicr.gsi.shesmu.server;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class SourceLocation implements Comparable<SourceLocation> {

  public interface SourceLoctionLinker {
    Stream<String> sourceUrl(String localFilePath, int line, int column, Instant time);
  }

  private final int column;
  private final String fileName;
  private final int line;

  private final Instant time;

  public SourceLocation(String fileName, int line, int column, Instant time) {
    super();
    this.fileName = fileName;
    this.line = line;
    this.column = column;
    this.time = time;
  }

  public int column() {
    return column;
  }

  @Override
  public int compareTo(SourceLocation o) {
    int comparison = fileName.compareTo(o.fileName);
    if (comparison == 0) {
      comparison = Integer.compare(line, o.line);
    }
    if (comparison == 0) {
      comparison = Integer.compare(column, o.column);
    }
    if (comparison == 0) {
      comparison = time.compareTo(o.time);
    }
    return comparison;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final SourceLocation other = (SourceLocation) obj;
    if (column != other.column) {
      return false;
    }
    if (fileName == null) {
      if (other.fileName != null) {
        return false;
      }
    } else if (!fileName.equals(other.fileName)) {
      return false;
    }
    if (line != other.line) {
      return false;
    }
    if (time == null) {
      if (other.time != null) {
        return false;
      }
    } else if (!time.equals(other.time)) {
      return false;
    }
    return true;
  }

  public String fileName() {
    return fileName;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + column;
    result = prime * result + (fileName == null ? 0 : fileName.hashCode());
    result = prime * result + line;
    result = prime * result + (time == null ? 0 : time.hashCode());
    return result;
  }

  public int line() {
    return line;
  }

  public Instant time() {
    return time;
  }

  public void toJson(ArrayNode array, SourceLoctionLinker linker) {
    final ObjectNode node = array.addObject();
    toJson(node, linker);
  }

  public void toJson(ObjectNode node, SourceLoctionLinker linker) {
    node.put("file", fileName);
    node.put("line", line);
    node.put("column", column);
    node.put("time", time.toEpochMilli());
    url(linker).ifPresent(url -> node.put("url", url));
  }

  @Override
  public String toString() {
    return String.format("%s:%d:%d[%s]", fileName, line, column, time);
  }

  public Optional<String> url(SourceLoctionLinker linker) {
    return linker.sourceUrl(fileName, line, column, time).filter(Objects::nonNull).findAny();
  }
}
