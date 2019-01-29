package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.server.SourceLocation.SourceLoctionLinker;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Translate JSON-formatted queries into Java objects and perform the query */
public class Query {
  public static class FilterAdded extends FilterJson {
    private Long end;

    private Long start;

    @Override
    public Filter convert() {
      return ActionProcessor.added(
          Optional.ofNullable(start).map(Instant::ofEpochMilli),
          Optional.ofNullable(end).map(Instant::ofEpochMilli));
    }

    public Long getEnd() {
      return end;
    }

    public Long getStart() {
      return start;
    }

    public void setEnd(Long end) {
      this.end = end;
    }

    public void setStart(Long start) {
      this.start = start;
    }
  }

  public static class FilterChecked extends FilterJson {
    private Long end;

    private Long start;

    @Override
    public Filter convert() {
      return ActionProcessor.checked(
          Optional.ofNullable(start).map(Instant::ofEpochMilli),
          Optional.ofNullable(end).map(Instant::ofEpochMilli));
    }

    public Long getEnd() {
      return end;
    }

    public Long getStart() {
      return start;
    }

    public void setEnd(Long end) {
      this.end = end;
    }

    public void setStart(Long start) {
      this.start = start;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
  @JsonSubTypes({
    @Type(value = FilterStatus.class, name = "status"),
    @Type(value = FilterAdded.class, name = "added"),
    @Type(value = FilterChecked.class, name = "checked"),
    @Type(value = FilterSourceFile.class, name = "sourcefile"),
    @Type(value = FilterSourceLocation.class, name = "sourcelocation"),
    @Type(value = FilterStatusChanged.class, name = "statuschanged"),
    @Type(value = FilterType.class, name = "type")
  })
  public abstract static class FilterJson {
    public abstract Filter convert();
  }

  public static class FilterSourceFile extends FilterJson {
    private String[] files;

    @Override
    public Filter convert() {
      return ActionProcessor.fromFile(files);
    }

    public String[] getFiles() {
      return files;
    }

    public void setFiles(String[] files) {
      this.files = files;
    }
  }

  public static class FilterSourceLocation extends FilterJson {
    private LocationJson[] locations;

    @Override
    public Filter convert() {
      return ActionProcessor.fromFile(Stream.of(locations));
    }

    public LocationJson[] getLocations() {
      return locations;
    }

    public void setLocations(LocationJson[] locations) {
      this.locations = locations;
    }
  }

  public static class FilterStatus extends FilterJson {
    private ActionState[] states;

    @Override
    public Filter convert() {
      return ActionProcessor.isState(states);
    }

    public ActionState[] getStates() {
      return states;
    }

    public void setState(ActionState[] states) {
      this.states = states;
    }
  }

  public static class FilterStatusChanged extends FilterJson {
    private Long end;

    private Long start;

    @Override
    public Filter convert() {
      return ActionProcessor.statusChanged(
          Optional.ofNullable(start).map(Instant::ofEpochMilli),
          Optional.ofNullable(end).map(Instant::ofEpochMilli));
    }

    public Long getEnd() {
      return end;
    }

    public Long getStart() {
      return start;
    }

    public void setEnd(Long end) {
      this.end = end;
    }

    public void setStart(Long start) {
      this.start = start;
    }
  }

  public static class FilterType extends FilterJson {
    private String[] types;

    @Override
    public Filter convert() {
      return ActionProcessor.type(types);
    }

    public String[] getTypes() {
      return types;
    }

    public void setTypes(String[] types) {
      this.types = types;
    }
  }

  private class Limiter<T> implements Predicate<T> {
    private long count;
    private final long limit;

    public Limiter(long hardLimit) {
      super();
      this.limit = Math.max(1, Math.min(getLimit(), hardLimit)) + Math.max(0, getSkip());
    }

    @Override
    public boolean test(T t) {
      return count++ < limit;
    }
  }

  public static final class LocationJson implements Predicate<SourceLocation> {
    private Integer column;
    private String file;
    private Integer line;
    private Long time;

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
      if (location.line() != line.intValue()) {
        return false;
      }
      if (column == null) {
        return true;
      }
      if (location.column() != column.intValue()) {
        return false;
      }
      if (time == null) {
        return true;
      }
      return location.time().toEpochMilli() == time.longValue();
    }
  }

  FilterJson[] filters;

  long limit = 100;

  long skip = 0;

  public FilterJson[] getFilters() {
    return filters;
  }

  public long getLimit() {
    return limit;
  }

  public long getSkip() {
    return skip;
  }

  public Response perform(
      ObjectMapper mapper, SourceLoctionLinker linker, ActionProcessor processor)
      throws IOException {
    final Filter[] filters =
        Arrays.stream(getFilters())
            .filter(Objects::nonNull)
            .map(FilterJson::convert)
            .toArray(Filter[]::new);
    final Response result = new Response();
    final Limiter<ObjectNode> limiter = new Limiter<>(500);
    result.setResults(
        processor
            .stream(mapper, linker, filters)
            .filter(limiter)
            .skip(Math.max(0, getSkip()))
            .toArray(ObjectNode[]::new));
    result.setTotal(limiter.count);
    return result;
  }

  public void setFilters(FilterJson[] filters) {
    this.filters = filters;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public void setSkip(long skip) {
    this.skip = skip;
  }
}
