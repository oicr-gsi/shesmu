package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;

public interface ExpressionCompilerServices {
  default Optional<TargetWithContext> captureOptional(ExpressionNode expression) {
    return Optional.empty();
  }

  FunctionDefinition function(String name);

  Imyhat imyhat(String name);

  InputFormatDefinition inputFormat();

  InputFormatDefinition inputFormat(String format);
}
