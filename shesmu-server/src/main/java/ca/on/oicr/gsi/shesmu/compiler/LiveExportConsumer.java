package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface LiveExportConsumer {
  final class DefineVariableExport {

    private final Flavour flavour;
    private final MethodHandle method;
    private final String name;
    private final Imyhat type;

    public DefineVariableExport(String name, Flavour flavour, Imyhat type, MethodHandle method) {
      this.flavour = flavour;
      this.name = name;
      this.type = type;
      this.method = method;
    }

    public Flavour flavour() {
      return flavour;
    }

    public MethodHandle method() {
      return method;
    }

    public String name() {
      return name;
    }

    public Imyhat type() {
      return type;
    }
  }

  void constant(MethodHandle method, String name, Imyhat type);

  void defineOlive(
      MethodHandle inputsHandle,
      MethodHandle method,
      String name,
      String inputFormatName,
      boolean isRoot,
      List<Imyhat> parameterTypes,
      List<DefineVariableExport> variables,
      List<DefineVariableExport> checks);

  void function(
      MethodHandle method,
      String name,
      Imyhat returnType,
      Supplier<Stream<FunctionParameter>> parameters);
}
