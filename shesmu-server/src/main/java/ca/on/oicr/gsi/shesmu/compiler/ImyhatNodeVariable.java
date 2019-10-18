package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeVariable extends ImyhatNode {
  private final String name;

  public ImyhatNodeVariable(String name) {
    super();
    this.name = name;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Imyhat result = expressionCompilerServices.imyhat(name);
    if (result == null) {
      errorHandler.accept(String.format("Unknown type “%s“.", name));
      return Imyhat.BAD;
    }
    return result;
  }
}
