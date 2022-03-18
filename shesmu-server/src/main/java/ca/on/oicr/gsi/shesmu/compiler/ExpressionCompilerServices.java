package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;

public interface ExpressionCompilerServices {
  default Optional<TargetWithContext> captureOptional(
      ExpressionNode expression, int line, int column, Consumer<String> errorHandler) {
    errorHandler.accept(
        String.format("%d:%d: Optional operation “?” must be inside of ``.", line, column));
    return Optional.empty();
  }

  FunctionDefinition function(String name);

  Imyhat imyhat(String name);

  InputFormatDefinition inputFormat();

  InputFormatDefinition inputFormat(String format);
}
