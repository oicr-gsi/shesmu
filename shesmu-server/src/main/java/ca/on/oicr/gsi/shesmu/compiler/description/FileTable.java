package ca.on.oicr.gsi.shesmu.compiler.description;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileTable {
  private final String bytecode;
  private final String filename;
  private final InputFormatDefinition format;
  private final String hash;
  private final List<OliveTable> olives;

  public FileTable(
      String filename,
      InputFormatDefinition format,
      String hash,
      String bytecode,
      Stream<OliveTable> olives) {
    super();
    this.filename = filename;
    this.format = format;
    this.hash = hash;
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

  public String hash() {
    return hash;
  }

  public Stream<OliveTable> olives() {
    return olives.stream();
  }
}
