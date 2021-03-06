package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MatchBranchNodeEmpty extends MatchBranchNode {

  public MatchBranchNodeEmpty(int line, int column, String name, ExpressionNode value) {
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
  protected Stream<EcmaLoadableValue> loadBoundNames(String base) {
    return Stream.empty();
  }

  @Override
  protected Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement) {
    return renderer;
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    if (argumentType.isSame(Imyhat.NOTHING)) {
      return true;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: “%s” is expecting no fields, but %s was found.",
              line(), column(), name(), argumentType.name()));
      return false;
    }
  }
}
