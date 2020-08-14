package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class AlgebraicImyhatNodeEmpty extends AlgebraicImyhatNode {

  private final String name;

  public AlgebraicImyhatNodeEmpty(String name) {
    super();
    this.name = name;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return Imyhat.algebraicTuple(name);
  }
}
