package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.server.SourceLocation.SourceLoctionLinker;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Translate JSON-formatted queries into Java objects and perform the query */
public class Query {
  public abstract static class AgoFilterJson extends FilterJson {
    private long offset;

    protected abstract Filter convert(Optional<Instant> start, Optional<Instant> end);

    @Override
    public final Filter convert() {
      return maybeNegate(convert(Optional.of(Instant.now().minusMillis(offset)), Optional.empty()));
    }

    public final long getOffset() {
      return offset;
    }

    public final void setOffset(long offset) {
      this.offset = offset;
    }
  }

  public abstract static class CollectionFilterJson extends FilterJson {
    private FilterJson[] filters;

    @Override
    public final Filter convert() {
      return maybeNegate(
          convert(Stream.of(filters).map(FilterJson::convert).toArray(Filter[]::new)));
    }

    protected abstract Filter convert(Filter[] filters);

    public final FilterJson[] getFilters() {
      return filters;
    }

    public final void setFilters(FilterJson[] filters) {
      this.filters = filters;
    }
  }

  public static class FilterAdded extends RangeFilterJson {
    @Override
    protected Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.added(start, end);
    }
  }

  public static class FilterAddedAgo extends AgoFilterJson {
    @Override
    protected Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.added(start, end);
    }
  }

  public static class FilterAnd extends CollectionFilterJson {
    @Override
    public Filter convert(Filter[] filters) {
      return ActionProcessor.and(filters);
    }
  }

  public static class FilterChecked extends RangeFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.checked(start, end);
    }
  }

  public static class FilterCheckedAgo extends AgoFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.checked(start, end);
    }
  }

  public static class FilterExternal extends RangeFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.external(start, end);
    }
  }

  public static class FilterExternalAgo extends AgoFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.external(start, end);
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @Type(value = FilterAdded.class, name = "added"),
    @Type(value = FilterAddedAgo.class, name = "addedago"),
    @Type(value = FilterAnd.class, name = "and"),
    @Type(value = FilterChecked.class, name = "checked"),
    @Type(value = FilterCheckedAgo.class, name = "checkedago"),
    @Type(value = FilterExternal.class, name = "external"),
    @Type(value = FilterExternalAgo.class, name = "externalago"),
    @Type(value = FilterOr.class, name = "or"),
    @Type(value = FilterRegex.class, name = "regex"),
    @Type(value = FilterSourceFile.class, name = "sourcefile"),
    @Type(value = FilterSourceLocation.class, name = "sourcelocation"),
    @Type(value = FilterStatus.class, name = "status"),
    @Type(value = FilterStatusChanged.class, name = "statuschanged"),
    @Type(value = FilterStatusChangedAgo.class, name = "statuschangedago"),
    @Type(value = FilterTag.class, name = "tag"),
    @Type(value = FilterText.class, name = "text"),
    @Type(value = FilterType.class, name = "type")
  })
  public abstract static class FilterJson {
    private boolean negate;

    public abstract Filter convert();

    public boolean isNegate() {
      return negate;
    }

    protected Filter maybeNegate(Filter filter) {
      return negate ? filter.negate() : filter;
    }

    public void setNegate(boolean negate) {
      this.negate = negate;
    }
  }

  public static class FilterOr extends CollectionFilterJson {
    @Override
    public Filter convert(Filter[] filters) {
      return ActionProcessor.or(filters);
    }
  }

  public static class FilterRegex extends FilterJson {
    private String pattern;

    @Override
    public Filter convert() {
      return maybeNegate(ActionProcessor.textSearch(Pattern.compile(pattern)));
    }

    public String getPattern() {
      return pattern;
    }

    public void setPattern(String pattern) {
      this.pattern = pattern;
    }
  }

  public static class FilterSourceFile extends FilterJson {
    private String[] files;

    @Override
    public Filter convert() {
      return maybeNegate(ActionProcessor.fromFile(files));
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
      return maybeNegate(ActionProcessor.fromFile(Stream.of(locations)));
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
      return maybeNegate(ActionProcessor.isState(states));
    }

    public ActionState[] getStates() {
      return states;
    }

    public void setState(ActionState[] states) {
      this.states = states;
    }
  }

  public static class FilterStatusChanged extends RangeFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.statusChanged(start, end);
    }
  }

  public static class FilterStatusChangedAgo extends AgoFilterJson {
    @Override
    public Filter convert(Optional<Instant> start, Optional<Instant> end) {
      return ActionProcessor.statusChanged(start, end);
    }
  }

  public static class FilterTag extends FilterJson {
    private String[] tags;

    @Override
    public Filter convert() {
      return maybeNegate(ActionProcessor.tags(Stream.of(tags)));
    }

    public String[] getTags() {
      return tags;
    }

    public void setTags(String[] tags) {
      this.tags = tags;
    }
  }

  public static class FilterText extends FilterJson {
    private boolean matchCase;
    private String text;

    @Override
    public Filter convert() {
      return maybeNegate(
          ActionProcessor.textSearch(
              Pattern.compile(
                  "^.*" + Pattern.quote(text) + ".*$", matchCase ? 0 : Pattern.CASE_INSENSITIVE)));
    }

    public String getText() {
      return text;
    }

    public boolean isMatchCase() {
      return matchCase;
    }

    public void setMatchCase(boolean matchCase) {
      this.matchCase = matchCase;
    }

    public void setText(String text) {
      this.text = text;
    }
  }

  public static class FilterType extends FilterJson {
    private String[] types;

    @Override
    public Filter convert() {
      return maybeNegate(ActionProcessor.type(types));
    }

    public String[] getTypes() {
      return types;
    }

    public void setTypes(String[] types) {
      this.types = types;
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

  public abstract static class RangeFilterJson extends FilterJson {
    private Long end;

    private Long start;

    protected abstract Filter convert(Optional<Instant> start, Optional<Instant> end);

    @Override
    public final Filter convert() {
      return maybeNegate(
          convert(
              Optional.ofNullable(start).map(Instant::ofEpochMilli),
              Optional.ofNullable(end).map(Instant::ofEpochMilli)));
    }

    public final Long getEnd() {
      return end;
    }

    public final Long getStart() {
      return start;
    }

    public final void setEnd(Long end) {
      this.end = end;
    }

    public final void setStart(Long start) {
      this.start = start;
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

  public void perform(OutputStream output, SourceLoctionLinker linker, ActionProcessor processor)
      throws IOException {
    final Filter[] filters =
        Arrays.stream(getFilters())
            .filter(Objects::nonNull)
            .map(FilterJson::convert)
            .toArray(Filter[]::new);
    final JsonGenerator jsonOutput = new JsonFactory().createGenerator(output, JsonEncoding.UTF8);
    jsonOutput.setCodec(RuntimeSupport.MAPPER);
    jsonOutput.writeStartObject();
    jsonOutput.writeNumberField("total", processor.size(filters));
    jsonOutput.writeArrayFieldStart("results");
    processor
        .stream(linker, filters)
        .skip(Math.max(0, getSkip()))
        .limit(limit)
        .forEach(
            action -> {
              try {
                jsonOutput.writeTree(action);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    jsonOutput.writeEndArray();
    jsonOutput.writeEndObject();
    jsonOutput.close();
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
