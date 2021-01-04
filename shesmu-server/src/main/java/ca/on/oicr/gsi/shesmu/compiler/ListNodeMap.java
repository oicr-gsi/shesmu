package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.function.Consumer;

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
  public DestructuredArgumentNode nextName(DestructuredArgumentNode inputs) {
    return nextName;
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  public EcmaLoadableConstructor render(EcmaStreamBuilder builder, EcmaLoadableConstructor name) {
    builder.map(name, expression.type(), expression::renderEcma);
    return nextName::renderEcma;
  }

  @Override
  protected boolean resolveExtraDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return nextName.resolve(expressionCompilerServices, errorHandler);
  }

  @Override
  protected Optional<Imyhat> typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    return nextName.typeCheck(expression.type(), errorHandler)
        ? Optional.of(expression.type())
        : Optional.empty();
  }
}
