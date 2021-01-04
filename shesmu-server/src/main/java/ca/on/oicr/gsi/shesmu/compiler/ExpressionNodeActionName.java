package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeActionName extends ExpressionNode {

  public ExpressionNodeActionName(int line, int column) {
    super(line, column);
  }

  @Override
  public Optional<String> dumpColumnName() {
    return Optional.of("Action Name");
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    if (predicate.test(Flavour.CONSTANT)) {
      names.add(BaseOliveBuilder.ACTION_NAME);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    // We are never in an olive in ES, so this is always null
    return "null";
  }

  @Override
  public void render(Renderer renderer) {
    renderer.emitNamed(BaseOliveBuilder.ACTION_NAME);
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
    return Imyhat.STRING.asOptional();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
