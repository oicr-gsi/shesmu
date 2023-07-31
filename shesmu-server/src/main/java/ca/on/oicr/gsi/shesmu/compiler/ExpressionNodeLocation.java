package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeLocation extends ExpressionNode {

  public ExpressionNodeLocation(int line, int column) {
    super(line, column);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.

  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    try {
      return RuntimeSupport.MAPPER.writeValueAsString(
          String.format("%s:%d:%d[%s]", renderer.sourcePath(), line(), column(), renderer.hash()));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void render(Renderer renderer) {
    renderer
        .methodGen()
        .push(
            String.format(
                "%s:%d:%d[%s]",
                renderer.root().sourcePath(), line(), column(), renderer.root().hash));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return Imyhat.STRING;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
