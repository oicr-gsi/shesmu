package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class FetchCollectNodeList extends FetchCollectNode {

  private final FetchNode inner;

  public FetchCollectNodeList(FetchNode inner) {
    this.inner = inner;
  }

  @Override
  public Imyhat comparatorType() {
    return inner.type();
  }

  @Override
  public String operation() {
    return "list";
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return inner.renderEcma(renderer);
  }

  @Override
  public boolean resolve(NameDefinitions collectorName, Consumer<String> errorHandler) {
    return inner.resolve(collectorName, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return inner.type().asList();
  }

  @Override
  public boolean typeCheck(Imyhat imyhat, Consumer<String> errorHandler) {
    return inner.typeCheck(errorHandler);
  }
}
