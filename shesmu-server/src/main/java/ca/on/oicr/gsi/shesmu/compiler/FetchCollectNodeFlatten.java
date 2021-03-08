package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.util.function.Consumer;

public class FetchCollectNodeFlatten extends FetchCollectNode {

  private final int line;
  private final int column;
  private final FetchNode inner;

  public FetchCollectNodeFlatten(int line, int column, FetchNode inner) {
    this.line = line;
    this.column = column;
    this.inner = inner;
  }

  @Override
  public Imyhat comparatorType() {
    return inner.type();
  }

  @Override
  public String operation() {
    return "flatten";
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
    if (inner.typeCheck(errorHandler)) {
      if (inner.type() instanceof ListImyhat) {
        return true;
      }
      errorHandler.accept(
          String.format("%d:%d: Expected list but got %s.", line, column, inner.type().name()));
    }
    return false;
  }
}
