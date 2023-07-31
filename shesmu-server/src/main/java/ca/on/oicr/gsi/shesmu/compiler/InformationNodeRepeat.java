package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InformationNodeRepeat extends InformationNodeBaseRepeat {

  private final List<InformationNode> collectors;

  public InformationNodeRepeat(
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      List<InformationNode> collectors) {
    super(name, source, transforms);
    this.collectors = collectors;
  }

  @Override
  protected String renderBlock(EcmaScriptRenderer renderer, String data) {
    return data;
  }

  @Override
  public String renderRow(EcmaScriptRenderer renderer) {
    return collectors.stream()
        .map(collector -> collector.renderEcma(renderer))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  @Override
  protected boolean resolveTerminal(NameDefinitions collectorName, Consumer<String> errorHandler) {
    return collectors.stream()
            .filter(collector -> collector.resolve(collectorName, errorHandler))
            .count()
        == collectors.size();
  }

  @Override
  protected boolean resolveTerminalDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return collectors.stream()
            .filter(
                collector ->
                    collector.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == collectors.size();
  }

  @Override
  protected boolean typeCheckTerminal(Consumer<String> errorHandler) {
    return collectors.stream().filter(collector -> collector.typeCheck(errorHandler)).count()
        == collectors.size();
  }
}
