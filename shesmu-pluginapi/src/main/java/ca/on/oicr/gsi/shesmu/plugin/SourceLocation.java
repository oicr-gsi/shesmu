package ca.on.oicr.gsi.shesmu.plugin;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class SourceLocation implements Comparable<SourceLocation> {

  public interface SourceLocationLinker {

    SourceLocationLinker EMPTY = (path, line, column, hash) -> Stream.empty();

    Stream<String> sourceUrl(String localFilePath, int line, int column, String hash);
  }

  public static final class SourceLocationSerializer extends JsonSerializer<SourceLocation> {
    private final SourceLocationLinker linker;

    public SourceLocationSerializer(SourceLocationLinker linker) {
      this.linker = linker;
    }

    @Override
    public void serialize(
        SourceLocation sourceLocation,
        JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider)
        throws IOException {
      jsonGenerator.writeStartObject();
      jsonGenerator.writeStringField("file", sourceLocation.fileName);
      jsonGenerator.writeNumberField("line", sourceLocation.line);
      jsonGenerator.writeNumberField("column", sourceLocation.column);
      jsonGenerator.writeStringField("hash", sourceLocation.hash);
      final var url = sourceLocation.url(linker);
      if (url.isPresent()) {
        jsonGenerator.writeStringField("url", url.get());
      }
      jsonGenerator.writeEndObject();
    }
  }

  private final int column;
  private final String fileName;
  private final String hash;
  private final int line;

  public SourceLocation(String fileName, int line, int column, String hash) {
    super();
    this.fileName = fileName;
    this.line = line;
    this.column = column;
    this.hash = hash;
  }

  public int column() {
    return column;
  }

  @Override
  public int compareTo(SourceLocation o) {
    var comparison = fileName.compareTo(o.fileName);
    if (comparison == 0) {
      comparison = Integer.compare(line, o.line);
    }
    if (comparison == 0) {
      comparison = Integer.compare(column, o.column);
    }
    if (comparison == 0) {
      comparison = hash.compareTo(o.hash);
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
    final var other = (SourceLocation) obj;
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
    if (hash == null) {
      return other.hash == null;
    } else return hash.equals(other.hash);
  }

  public String fileName() {
    return fileName;
  }

  public String hash() {
    return hash;
  }

  @Override
  public int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + column;
    result = prime * result + (fileName == null ? 0 : fileName.hashCode());
    result = prime * result + line;
    result = prime * result + (hash == null ? 0 : hash.hashCode());
    return result;
  }

  public int line() {
    return line;
  }

  public void toJson(ArrayNode array, SourceLocationLinker linker) {
    final var node = array.addObject();
    toJson(node, linker);
  }

  public void toJson(ObjectNode node, SourceLocationLinker linker) {
    node.put("file", fileName);
    node.put("line", line);
    node.put("column", column);
    node.put("hash", hash);
    url(linker).ifPresent(url -> node.put("url", url));
  }

  @Override
  public String toString() {
    return String.format("%s:%d:%d[%s]", fileName, line, column, hash);
  }

  public Optional<String> url(SourceLocationLinker linker) {
    return linker.sourceUrl(fileName, line, column, hash).filter(Objects::nonNull).findAny();
  }
}
