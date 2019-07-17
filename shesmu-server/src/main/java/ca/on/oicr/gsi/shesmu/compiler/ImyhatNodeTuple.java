package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImyhatNodeTuple extends ImyhatNode {

  private final List<ImyhatNode> types;

  public ImyhatNodeTuple(List<ImyhatNode> types) {
    this.types = types;
  }

  @Override
  public Imyhat render(
      Function<String, Imyhat> definedTypes,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler) {
    return Imyhat.tuple(
        types
            .stream()
            .map(t -> t.render(definedTypes, definedFunctions, errorHandler))
            .toArray(Imyhat[]::new));
  }
}
