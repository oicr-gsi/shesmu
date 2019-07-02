package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ExpressionNodeBoolean extends ExpressionNode {

  private final boolean value;

  public ExpressionNodeBoolean(int line, int column, boolean value) {
    super(line, column);
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().push(value);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
