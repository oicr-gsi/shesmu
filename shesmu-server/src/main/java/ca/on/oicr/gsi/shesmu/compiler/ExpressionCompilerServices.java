package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public interface ExpressionCompilerServices {
  FunctionDefinition function(String name);

  Imyhat imyhat(String name);

  InputFormatDefinition inputFormat();
}
