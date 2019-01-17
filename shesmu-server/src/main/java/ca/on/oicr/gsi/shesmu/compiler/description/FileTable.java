package ca.on.oicr.gsi.shesmu.compiler.description;

import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileTable {
  private final String bytecode;
  private final String filename;
  private final InputFormatDefinition format;
  private final List<OliveTable> olives;
  private final Instant timestamp;

  public FileTable(
      String filename,
      InputFormatDefinition format,
      Instant timestamp,
      String bytecode,
      Stream<OliveTable> olives) {
    super();
    this.filename = filename;
    this.format = format;
    this.timestamp = timestamp;
    this.bytecode = bytecode;
    this.olives = olives.collect(Collectors.toList());
  }

  public String bytecode() {
    return bytecode;
  }

  public String filename() {
    return filename;
  }

  public InputFormatDefinition format() {
    return format;
  }

  public Stream<OliveTable> olives() {
    return olives.stream();
  }

  public Instant timestamp() {
    return timestamp;
  }
}
