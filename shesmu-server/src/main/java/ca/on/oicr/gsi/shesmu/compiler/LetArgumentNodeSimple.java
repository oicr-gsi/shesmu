package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public final class LetArgumentNodeSimple extends LetArgumentNode {
  public LetArgumentNodeSimple(DestructuredArgumentNode name, ExpressionNode expression) {
    super(name, expression);
  }

  @Override
  public boolean filters() {
    return false;
  }

  @Override
  public Consumer<Renderer> render(LetBuilder let, Imyhat type, Consumer<Renderer> loadLocal) {
    return loadLocal;
  }

  @Override
  public boolean typeCheck(
      int line,
      int column,
      Imyhat type,
      DestructuredArgumentNode name,
      Consumer<String> errorHandler) {
    return name.typeCheck(type, errorHandler);
  }
}
