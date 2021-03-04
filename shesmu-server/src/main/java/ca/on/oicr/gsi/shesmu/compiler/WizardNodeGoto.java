package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeGoto extends WizardNode {
  private final List<ExpressionNode> arguments;
  private final int column;
  private WizardDefineNode definition;
  private final int line;
  private final String name;

  public WizardNodeGoto(int line, int column, String name, List<ExpressionNode> arguments) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.arguments = arguments;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {

    return arguments.stream()
        .map(arg -> arg.renderEcma(renderer))
        .collect(Collectors.joining(", ", definition.function() + "(", ")"));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return arguments.stream().filter(arg -> arg.resolve(defs, errorHandler)).count()
        == arguments.size();
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    definition = references.get(name);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: No step %s is defined to go to.", line, column, name));
      return false;
    } else {
      return true;
    }
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return arguments.stream()
            .filter(arg -> arg.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == arguments.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (arguments.stream().filter(arg -> arg.typeCheck(errorHandler)).count() == arguments.size()) {
      return definition.checkParameters(
          line,
          column,
          arguments.stream().map(ExpressionNode::type).collect(Collectors.toList()),
          errorHandler);
    }
    ;
    return false;
  }
}
