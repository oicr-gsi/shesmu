package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeList extends ImyhatNode {
  private final ImyhatNode inner;

  public ImyhatNodeList(ImyhatNode inner) {
    super();
    this.inner = inner;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.render(expressionCompilerServices, errorHandler).asList();
  }
}
