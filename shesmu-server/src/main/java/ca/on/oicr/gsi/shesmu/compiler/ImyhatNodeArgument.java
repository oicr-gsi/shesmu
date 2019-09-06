package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler) {
    final FunctionDefinition definition = definedFunctions.apply(function);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown function %s for parameter type.", line, column, function));
      return Imyhat.BAD;
    }
    final Optional<Imyhat> argType =
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
