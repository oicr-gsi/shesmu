package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface ExportConsumer {
  void constant(String name, Imyhat type);

  void definition(
      String name,
      String inputFormat,
      boolean root,
      List<Imyhat> parameters,
      List<Target> outputVariables);

  void function(String name, Imyhat returnType, Supplier<Stream<FunctionParameter>> parameters);
}
