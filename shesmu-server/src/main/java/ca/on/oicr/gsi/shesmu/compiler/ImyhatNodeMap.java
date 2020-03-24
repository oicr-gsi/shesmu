package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeMap extends ImyhatNode {

  private final ImyhatNode key;
  private final ImyhatNode value;

  public ImyhatNodeMap(ImyhatNode key, ImyhatNode value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return Imyhat.dictionary(
        key.render(expressionCompilerServices, errorHandler),
        value.render(expressionCompilerServices, errorHandler));
  }
}
