package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MatchBranchNodeDiscard extends MatchBranchNode {

  public MatchBranchNodeDiscard(int line, int column, String name, ExpressionNode value) {
    super(line, column, name, value);
  }

  @Override
  protected NameDefinitions bind(NameDefinitions definitions) {
    return definitions;
  }

  @Override
  protected Stream<Target> boundNames() {
    return Stream.empty();
  }

  @Override
  protected Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement) {
    return renderer;
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    return true;
  }
}
