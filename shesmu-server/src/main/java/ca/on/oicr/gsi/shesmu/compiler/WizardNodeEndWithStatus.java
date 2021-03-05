package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeEndWithStatus extends WizardNode {
  private final List<InformationNode> information;
  private final ExpressionNode status;

  public WizardNodeEndWithStatus(List<InformationNode> information, ExpressionNode status) {
    this.information = information;
    this.status = status;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{information: %s, then: {type: \"status\", status: %s}}",
        information.stream()
            .map(i -> i.renderEcma(renderer))
            .collect(Collectors.joining(", ", "[", "]")),
        status.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return information.stream().filter(i -> i.resolve(defs, errorHandler)).count()
            == information.size()
        & status.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return information.stream()
                .filter(
                    i ->
                        i.resolveDefinitions(
                            expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == information.size()
        & status.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = status.typeCheck(errorHandler);
    if (ok) {
      if (!status.type().isSame(Imyhat.BOOLEAN)) {
        ok = false;
        status.typeError(Imyhat.BOOLEAN, status.type(), errorHandler);
      }
    }

    return ok
        & information.stream().filter(i -> i.typeCheck(errorHandler)).count() == information.size();
  }
}
