package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.function.Function;

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
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler) {
    final FunctionDefinition definition = definedFunctions.apply(function);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown function %d for return type.", line, column, function));
      return Imyhat.BAD;
    } else {
      return definition.returnType();
    }
  }
}
