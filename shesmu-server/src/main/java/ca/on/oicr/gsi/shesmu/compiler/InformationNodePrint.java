package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InformationNodePrint extends InformationNode {
  private final List<DisplayNode> contents;

  public InformationNodePrint(List<DisplayNode> contents) {
    this.contents = contents;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{type: \"display\", contents: %s}",
        contents.stream()
            .map(e -> e.renderEcma(renderer))
            .collect(Collectors.joining(",", "[", "]")));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return contents.stream().filter(e -> e.resolve(defs, errorHandler)).count() == contents.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return contents.stream()
            .filter(
                e ->
                    e.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == contents.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return contents.stream().filter(e -> e.typeCheck(errorHandler)).count() == contents.size();
  }
}
