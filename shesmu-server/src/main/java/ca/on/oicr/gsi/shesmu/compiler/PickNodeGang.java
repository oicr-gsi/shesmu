package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PickNodeGang extends PickNode {
  private final int line;
  private final int column;
  private final String name;
  private List<String> names = List.of();

  public PickNodeGang(int line, int column, String name) {
    super();
    this.line = line;
    this.column = column;
    this.name = name;
  }

  @Override
  public boolean isGood(InputFormatDefinition inputFormat, Consumer<String> errorHandler) {
    final var result = inputFormat.gangs().filter(g -> g.name().equals(name)).findFirst();
    if (result.isEmpty()) {
      errorHandler.accept(String.format("%d:%d: Unknown gang “%s”.", line, column, name));
    } else {
      names = result.get().elements().map(GangElement::name).collect(Collectors.toList());
    }
    return result.isPresent();
  }

  @Override
  public Stream<String> names() {
    return names.stream();
  }
}
