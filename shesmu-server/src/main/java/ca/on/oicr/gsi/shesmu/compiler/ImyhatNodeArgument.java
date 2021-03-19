package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeArgument extends ImyhatNode {
  private final int line;
  private final int column;
  private final String function;
  private final int index;

  public ImyhatNodeArgument(int line, int column, String function, int index) {
    this.line = line;
    this.column = column;
    this.function = function;
    this.index = index;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var definition = expressionCompilerServices.function(function);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown function %s for parameter type.", line, column, function));
      return Imyhat.BAD;
    }
    final var argType =
        definition.parameters().skip(index).findFirst().map(FunctionParameter::type);
    if (argType.isPresent()) {
      return argType.get();
    } else {
      errorHandler.accept(
          String.format("%d:%d: Function %s has no parameter %d.", line, column, function, index));
      return Imyhat.BAD;
    }
  }
}
