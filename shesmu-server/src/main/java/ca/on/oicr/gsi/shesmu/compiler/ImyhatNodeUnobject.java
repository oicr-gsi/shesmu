package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.Imyhat;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImyhatNodeUnobject extends ImyhatNode {
  private final String field;
  private final ImyhatNode outer;

  public ImyhatNodeUnobject(ImyhatNode outer, String field) {
    super();
    this.outer = outer;
    this.field = field;
  }

  @Override
  public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    final Imyhat type = outer.render(definedTypes, errorHandler);
    if (type instanceof Imyhat.ObjectImyhat) {
      final Imyhat inner = ((Imyhat.ObjectImyhat) type).get(field);
      if (inner.isBad()) {
        errorHandler.accept(
            String.format("Object type %s does not contain an field %s.", type.name(), field));
      }
      return inner;
    }
    errorHandler.accept(
        String.format("Type %s is not an object and it must be to destructure.", type.name()));
    return Imyhat.BAD;
  }
}
