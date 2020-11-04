package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.function.Consumer;

public class InformationParameterNodeLiteral<T> extends InformationParameterNode<T> {

  private final T value;
  private final boolean canFlatten;

  public InformationParameterNodeLiteral(T value, boolean canFlatten) {
    super();
    this.value = value;
    this.canFlatten = canFlatten;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    try {
      return canFlatten
          ? String.format("[%s]", RuntimeSupport.MAPPER.writeValueAsString(value))
          : RuntimeSupport.MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
