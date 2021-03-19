package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeUntuple extends ImyhatNode {
  private final int index;
  private final ImyhatNode outer;

  public ImyhatNodeUntuple(ImyhatNode outer, int index) {
    super();
    this.outer = outer;
    this.index = index;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final var type = outer.render(expressionCompilerServices, errorHandler);
    if (type instanceof Imyhat.TupleImyhat) {
      final var inner = ((Imyhat.TupleImyhat) type).get(index);
      if (inner.isBad()) {
        errorHandler.accept(
            String.format(
                "Tuple type %s does not contain an element at index %d.", type.name(), index));
      }
      return inner;
    }
    errorHandler.accept(
        String.format("Type %s is not a tuple and it must be to destructure.", type.name()));
    return Imyhat.BAD;
  }
}
