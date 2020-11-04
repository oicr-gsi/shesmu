package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.function.Consumer;

public class WizardMatchAlternativeNodeEmpty extends WizardMatchAlternativeNode {

  @Override
  public String render(
      EcmaScriptRenderer renderer,
      EcmaLoadableConstructor name,
      EcmaScriptRenderer localRenderer,
      String original) {
    renderer.statement(
        "throw new Error(\"Unsupported algebraic value in “Match” with no alternative.\")");
    return "null";
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefininitions,
      Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean typeCheck(
      int line, int column, Map<String, Imyhat> remainingBranches, Consumer<String> errorHandler) {
    if (remainingBranches.isEmpty()) {
      return true;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Branches are not exhaustive. Add “Else” or %s.",
              line, column, String.join(" and ", remainingBranches.keySet())));
      return false;
    }
  }
}
