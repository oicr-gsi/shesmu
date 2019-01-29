package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.function.Function;

public class ImyhatNodeList extends ImyhatNode {
  private final ImyhatNode inner;

  public ImyhatNodeList(ImyhatNode inner) {
    super();
    this.inner = inner;
  }

  @Override
  public Imyhat render(Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    return inner.render(definedTypes, errorHandler).asList();
  }
}
