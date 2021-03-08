package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class FetchCollectNodeDictionary extends FetchCollectNode {

  private final FetchNode value;
  private final ExpressionNode key;

  public FetchCollectNodeDictionary(ExpressionNode key, FetchNode value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public Imyhat comparatorType() {
    return key.type();
  }

  @Override
  public String operation() {
    return "dictionary";
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{key: %s, value: %s}", key.renderEcma(renderer), value.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions collectorName, Consumer<String> errorHandler) {
    return key.resolve(collectorName, errorHandler) & value.resolve(collectorName, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return key.resolveDefinitions(expressionCompilerServices, errorHandler)
        & value.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.dictionary(key.type(), value.type());
  }

  @Override
  public boolean typeCheck(Imyhat imyhat, Consumer<String> errorHandler) {
    return key.typeCheck(errorHandler) & value.typeCheck(errorHandler);
  }
}
