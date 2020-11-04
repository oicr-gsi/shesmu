package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DisplayNodeList extends DisplayNode {

  private final List<DisplayNode> items;

  public DisplayNodeList(List<DisplayNode> items) {
    this.items = items;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return items
        .stream()
        .map(item -> item.renderEcma(renderer))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return items
            .stream()
            .filter(
                item ->
                    item.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == items.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
  }
}
