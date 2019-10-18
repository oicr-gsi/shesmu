package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PickNodeGang extends PickNode {
  private final int line;
  private final int column;
  private final String name;
  private List<String> names = Collections.emptyList();

  public PickNodeGang(int line, int column, String name) {
    super();
    this.line = line;
    this.column = column;
    this.name = name;
  }

  @Override
  public boolean isGood(InputFormatDefinition inputFormat, Consumer<String> errorHandler) {
    final Optional<? extends GangDefinition> result =
        inputFormat.gangs().filter(g -> g.name().equals(name)).findFirst();
    if (!result.isPresent()) {
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
