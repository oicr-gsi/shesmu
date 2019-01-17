package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.Imyhat;
import java.util.function.Consumer;

public class ListNodeMap extends ListNodeWithExpression {

  private final String nextName;

  public ListNodeMap(int line, int column, String nextName, ExpressionNode expression) {
    super(line, column, expression);
    this.nextName = nextName;
  }

  @Override
  protected void finishMethod(Renderer renderer) {
    // Do nothing.
  }

  @Override
  protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
    return builder.map(line(), column(), name(), expression.type(), loadables);
  }

  @Override
  public String nextName() {
    return nextName;
  }

  @Override
  public Imyhat nextType() {
    return expression.type();
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
    return true;
  }
}
