package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

public class ListNodeSkip extends ListNodeBaseRange {

  public ListNodeSkip(int line, int column, ExpressionNode expression) {
    super(line, column, expression);
  }

  @Override
  protected void render(JavaStreamBuilder builder, Consumer<Renderer> expression) {
    builder.skip(expression);
  }
}
