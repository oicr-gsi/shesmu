package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeInputVariable extends ImyhatNode {
  private final int column;
  private final String inputFormat;
  private final int line;
  private final String variable;

  public ImyhatNodeInputVariable(int line, int column, String inputFormat, String variable) {
    this.line = line;
    this.column = column;
    this.inputFormat = inputFormat;
    this.variable = variable;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var definition = expressionCompilerServices.inputFormat(inputFormat);
    if (definition == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format %s.", line, column, inputFormat));
      return Imyhat.BAD;
    }
    final var varType =
        definition
            .baseStreamVariables()
            .filter(v -> v.name().equals(variable))
            .map(InputVariable::type)
            .findAny();
    if (varType.isEmpty()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Input format %s has no variable %s.", line, column, inputFormat, variable));
    }
    return varType.orElse(Imyhat.BAD);
  }
}
