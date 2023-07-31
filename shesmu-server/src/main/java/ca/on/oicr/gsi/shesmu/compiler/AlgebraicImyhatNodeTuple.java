package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;

public class AlgebraicImyhatNodeTuple extends AlgebraicImyhatNode {

  private final String name;
  private final List<ImyhatNode> types;

  public AlgebraicImyhatNodeTuple(String name, List<ImyhatNode> types) {
    super();
    this.name = name;
    this.types = types;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return Imyhat.algebraicTuple(
        name,
        types.stream()
            .map(t -> t.render(expressionCompilerServices, errorHandler))
            .toArray(Imyhat[]::new));
  }
}
