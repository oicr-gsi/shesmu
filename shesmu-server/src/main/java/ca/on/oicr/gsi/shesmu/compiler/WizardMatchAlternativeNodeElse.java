package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.function.Consumer;

public class WizardMatchAlternativeNodeElse extends WizardMatchAlternativeNode {

  private final WizardNode expression;

  public WizardMatchAlternativeNodeElse(WizardNode expression) {
    this.expression = expression;
  }

  @Override
  public String render(
      EcmaScriptRenderer renderer,
      EcmaLoadableConstructor name,
      EcmaScriptRenderer localRenderer,
      String original) {
    return expression.renderEcma(renderer, name);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return expression.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefininitions,
      Consumer<String> errorHandler) {
    return expression.resolveDefinitions(
        expressionCompilerServices, nativeDefininitions, errorHandler);
  }

  @Override
  public boolean typeCheck(
      int line, int column, Map<String, Imyhat> remainingBranches, Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
