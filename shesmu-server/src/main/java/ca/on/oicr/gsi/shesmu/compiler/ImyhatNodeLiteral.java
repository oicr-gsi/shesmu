package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeLiteral extends ImyhatNode {
  private final Imyhat type;

  public ImyhatNodeLiteral(Imyhat type) {
    super();
    this.type = type;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return type;
  }
}
