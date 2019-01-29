package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImyhatNodeUntuple extends ImyhatNode {
  private final int index;
  private final ImyhatNode outer;

  public ImyhatNodeUntuple(ImyhatNode outer, int index) {
    super();
    this.outer = outer;
    this.index = index;
  }

  @Override
  public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    final Imyhat type = outer.render(definedTypes, errorHandler);
    if (type instanceof Imyhat.TupleImyhat) {
      final Imyhat inner = ((Imyhat.TupleImyhat) type).get(index);
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
