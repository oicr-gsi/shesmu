package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface LiveExportConsumer {
  void constant(MethodHandle method, String name, Imyhat type);

  void function(
      MethodHandle method,
      String name,
      Imyhat returnType,
      Supplier<Stream<FunctionParameter>> parameters);
}
