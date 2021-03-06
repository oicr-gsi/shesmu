package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeReturn extends ImyhatNode {
  private final int line;
  private final int column;
  private final String function;

  public ImyhatNodeReturn(int line, int column, String function) {
    this.line = line;
    this.column = column;
    this.function = function;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var definition = expressionCompilerServices.function(function);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown function %s for return type.", line, column, function));
      return Imyhat.BAD;
    } else {
      return definition.returnType();
    }
  }
}
