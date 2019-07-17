package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface ExportConsumer {
  void function(String name, Imyhat returnType, Supplier<Stream<FunctionParameter>> parameters);
}
