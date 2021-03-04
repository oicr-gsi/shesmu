package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeChoice extends WizardNode {
  private final List<InformationNode> information;
  private final List<Pair<String, WizardNode>> choices;
  private final int line;
  private final int column;

  public WizardNodeChoice(
      int line,
      int column,
      List<InformationNode> information,
      List<Pair<String, WizardNode>> choices) {
    this.line = line;
    this.column = column;
    this.information = information;
    this.choices = choices;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final String choiceObject =
        choices.stream()
            .map(
                c -> {
                  try {
                    return RuntimeSupport.MAPPER.writeValueAsString(c.first())
                        + ": "
                        + renderer.lambda(0, (r, a) -> c.second().renderEcma(r));
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.joining(",", "{", "}"));
    return String.format(
        "{information: %s, then: {type: \"choice\", choices: %s}}",
        information.stream()
            .map(i -> i.renderEcma(renderer))
            .collect(Collectors.joining(", ", "[", "]")),
        choiceObject);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Map.Entry<String, Long> choice :
        choices.stream()
            .collect(Collectors.groupingBy(Pair::first, Collectors.counting()))
            .entrySet()) {
      if (choice.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Choice %s appears %d times.",
                line, column, choice.getKey(), choice.getValue()));
        ok = false;
      }
    }
    return ok
        & information.stream().filter(i -> i.resolve(defs, errorHandler)).count()
            == information.size()
        & choices.stream().filter(c -> c.second().resolve(defs, errorHandler)).count()
            == choices.size();
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return choices.stream()
            .filter(c -> c.second().resolveCrossReferences(references, errorHandler))
            .count()
        == choices.size();
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
        & choices.stream()
                .filter(
                    c ->
                        c.second()
                            .resolveDefinitions(
                                expressionCompilerServices, nativeDefinitions, errorHandler))
                .count()
            == choices.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return information.stream().filter(i -> i.typeCheck(errorHandler)).count() == information.size()
        & choices.stream().filter(c -> c.second().typeCheck(errorHandler)).count()
            == choices.size();
  }
}
