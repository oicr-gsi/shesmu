package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class InformationNodeSimulationExisting extends InformationNode {
  private final List<ObjectElementNode> constants;
  private final String script;

  public InformationNodeSimulationExisting(List<ObjectElementNode> constants, String script) {
    this.constants = constants;
    this.script = script;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    try {
      return String.format(
          "{ type: \"simulation-existing\", fileName: %s, parameters: %s }",
          RuntimeSupport.MAPPER.writeValueAsString(script),
          constants
              .stream()
              .flatMap(e -> e.renderConstant(renderer))
              .sorted()
              .collect(Collectors.joining(", ", "{", "}")));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return constants.stream().filter(c -> c.resolve(defs, errorHandler)).count()
        == constants.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return constants
            .stream()
            .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == constants.size();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return constants.stream().filter(c -> c.typeCheck(errorHandler)).count() == constants.size();
  }
}
