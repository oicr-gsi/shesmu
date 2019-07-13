package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ListNodeFilter extends ListNodeWithExpression {

  public ListNodeFilter(int line, int column, ExpressionNode expression) {
    super(line, column, expression);
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    // Do nothing.
  }

  @Override
  protected Pair<Renderer, LoadableConstructor> makeMethod(
      JavaStreamBuilder builder, LoadableConstructor name, LoadableValue[] loadables) {
    return new Pair<>(builder.filter(line(), column(), name, loadables), name);
  }

  @Override
  public List<Target> nextName(List<Target> inputs) {
    return inputs;
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    if (expression.type().isSame(Imyhat.BOOLEAN)) {
      return Optional.of(incoming);
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Filter expression must be boolean, but got %s.",
              line(), column(), expression.type().name()));
      return Optional.empty();
    }
  }
}
