package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ListNodeMap extends ListNodeWithExpression {

  private final DestructuredArgumentNode nextName;

  public ListNodeMap(
      int line, int column, DestructuredArgumentNode nextName, ExpressionNode expression) {
    super(line, column, expression);
    this.nextName = nextName;
    nextName.setFlavour(Target.Flavour.LAMBDA);
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    // Do nothing.
  }

  @Override
  protected Pair<Renderer, LoadableConstructor> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables) {
    return new Pair<>(
        builder.map(line(), column(), name, expression.type(), loadables), nextName::render);
  }

  @Override
  public List<Target> nextName(List<Target> inputs) {
    return nextName.targets().collect(Collectors.toList());
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    return nextName.typeCheck(expression.type(), errorHandler)
        ? Optional.of(expression.type())
        : Optional.empty();
  }
}
