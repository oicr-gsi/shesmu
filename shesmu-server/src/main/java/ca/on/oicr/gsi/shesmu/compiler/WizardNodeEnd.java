package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeEnd extends WizardNode {
  private final List<InformationNode> information;

  public WizardNodeEnd(List<InformationNode> information) {
    this.information = information;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer, EcmaLoadableConstructor name) {
    return renderer.newConst(
        renderer.lambda(
            1,
            (r, a) -> {
              name.create(rr -> a.apply(0)).forEach(r::define);
              return String.format(
                  "{information: %s, then: null}",
                  information
                      .stream()
                      .map(i -> i.renderEcma(r))
                      .collect(Collectors.joining(", ", "[", "]")));
            }));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {

    return information.stream().filter(i -> i.resolve(defs, errorHandler)).count()
        == information.size();
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
    return information
            .stream()
            .filter(
                i ->
                    i.resolveDefinitions(
                        expressionCompilerServices, nativeDefinitions, errorHandler))
            .count()
        == information.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return information.stream().filter(i -> i.typeCheck(errorHandler)).count()
        == information.size();
  }
}
