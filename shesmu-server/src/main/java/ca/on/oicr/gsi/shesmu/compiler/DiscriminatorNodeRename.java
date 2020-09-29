package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class DiscriminatorNodeRename extends DiscriminatorNodeBaseManipulated {

  public DiscriminatorNodeRename(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    super(expression, name);
  }

  @Override
  protected Imyhat convertType(Imyhat original, Consumer<String> errorHandler) {
    return original;
  }

  @Override
  protected void render(Renderer renderer) {
    // Do nothing.
  }
}
