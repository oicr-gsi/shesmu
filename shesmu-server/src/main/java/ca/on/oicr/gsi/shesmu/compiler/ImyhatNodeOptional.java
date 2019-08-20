package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImyhatNodeOptional extends ImyhatNode {
  private final ImyhatNode inner;

  public ImyhatNodeOptional(ImyhatNode inner) {
    super();
    this.inner = inner;
  }

  @Override
  public Imyhat render(
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler) {
    return inner.render(definedTypes, definedFunctions, errorHandler).asOptional();
  }
}
