package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import java.util.function.Predicate;

public final class SourceOliveLocation implements Predicate<SourceLocation> {
  private Integer column;
  private String file;
  private String hash;
  private Integer line;

  public SourceOliveLocation() {}

  public SourceOliveLocation(SourceOliveLocation original) {
    this.column = original.column;
    this.file = original.file;
    this.line = original.line;
    this.hash = original.hash;
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
    final SourceOliveLocation other = (SourceOliveLocation) obj;
    if (column == null) {
      if (other.column != null) {
        return false;
      }
    } else if (!column.equals(other.column)) {
      return false;
    }
    if (file == null) {
      if (other.file != null) {
        return false;
      }
    } else if (!file.equals(other.file)) {
      return false;
    }
    if (line == null) {
      if (other.line != null) {
        return false;
      }
    } else if (!line.equals(other.line)) {
      return false;
    }
    if (hash == null) {
      if (other.hash != null) {
        return false;
      }
    } else if (!hash.equals(other.hash)) {
      return false;
    }
    return true;
  }

  public Integer getColumn() {
    return column;
  }

  public String getFile() {
    return file;
  }

  public String getHash() {
    return hash;
  }

  public Integer getLine() {
    return line;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (column == null ? 0 : column.hashCode());
    result = prime * result + (file == null ? 0 : file.hashCode());
    result = prime * result + (line == null ? 0 : line.hashCode());
    result = prime * result + (hash == null ? 0 : hash.hashCode());
    return result;
  }

  public void setColumn(Integer column) {
    this.column = column;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public void setHash(String hash) {
    this.hash = hash;
  }

  public void setLine(Integer line) {
    this.line = line;
  }

  @Override
  public boolean test(SourceLocation location) {
    if (file == null || !file.equals(location.fileName())) {
      return false;
    }

    if (line == null) {
      return true;
    }
    if (location.line() != line) {
      return false;
    }
    if (column == null) {
      return true;
    }
    if (location.column() != column) {
      return false;
    }
    if (hash == null) {
      return true;
    }
    return location.hash().equals(hash);
  }

  @Override
  public String toString() {
    final StringBuilder buffer = new StringBuilder();
    buffer.append("\"").append(file).append("\"");
    if (line != null && line != 0) {
      buffer.append(":").append(line);
      if (column != null && column != 0) {
        buffer.append(":").append(column);
        if (hash != null) {
          buffer.append("[").append(hash).append("]");
        }
      }
    }
    return buffer.toString();
  }
}
