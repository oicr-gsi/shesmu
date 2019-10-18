package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PickNodeSimple extends PickNode {
  private final String name;

  public PickNodeSimple(String name) {
    super();
    this.name = name;
  }

  @Override
  public boolean isGood(InputFormatDefinition inputFormat, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Stream<String> names() {
    return Stream.of(name);
  }
}
