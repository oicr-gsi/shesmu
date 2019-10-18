package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class ImyhatNodeUncontainer extends ImyhatNode {
  private final ImyhatNode outer;

  public ImyhatNodeUncontainer(ImyhatNode outer) {
    super();
    this.outer = outer;
  }

  @Override
  public Imyhat render(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Imyhat type = outer.render(expressionCompilerServices, errorHandler);
    if (type instanceof Imyhat.ListImyhat) {
      return ((Imyhat.ListImyhat) type).inner();
    }
    if (type instanceof Imyhat.OptionalImyhat) {
      return ((Imyhat.OptionalImyhat) type).inner();
    }
    errorHandler.accept(
        String.format("Type %s must be list or optional to have something inside.", type.name()));
    return Imyhat.BAD;
  }
}
