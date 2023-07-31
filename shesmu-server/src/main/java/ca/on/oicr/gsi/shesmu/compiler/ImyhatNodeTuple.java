package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;

public class ImyhatNodeTuple extends ImyhatNode {

  private final List<ImyhatNode> types;

  public ImyhatNodeTuple(List<ImyhatNode> types) {
    this.types = types;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return Imyhat.tuple(
        types.stream()
            .map(t -> t.render(expressionCompilerServices, errorHandler))
            .toArray(Imyhat[]::new));
  }
}
