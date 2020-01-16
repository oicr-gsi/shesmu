package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import java.util.function.Predicate;

public final class LocationJson implements Predicate<SourceLocation> {
  private Integer column;
  private String file;
  private Integer line;
  private Long time;

  public LocationJson() {}

  public LocationJson(LocationJson original) {
    this.column = original.column;
    this.file = original.file;
    this.line = original.line;
    this.time = original.time;
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
    final LocationJson other = (LocationJson) obj;
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
    if (time == null) {
      if (other.time != null) {
        return false;
      }
    } else if (!time.equals(other.time)) {
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

  public Integer getLine() {
    return line;
  }

  public Long getTime() {
    return time;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (column == null ? 0 : column.hashCode());
    result = prime * result + (file == null ? 0 : file.hashCode());
    result = prime * result + (line == null ? 0 : line.hashCode());
    result = prime * result + (time == null ? 0 : time.hashCode());
    return result;
  }

  public void setColumn(Integer column) {
    this.column = column;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public void setLine(Integer line) {
    this.line = line;
  }

  public void setTime(Long time) {
    this.time = time;
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
    if (time == null) {
      return true;
    }
    return location.time().toEpochMilli() == time;
  }
}
