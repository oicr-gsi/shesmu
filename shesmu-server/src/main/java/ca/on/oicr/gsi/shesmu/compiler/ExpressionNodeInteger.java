package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ExpressionNodeInteger extends ExpressionNode {

  private final long value;

  public ExpressionNodeInteger(int line, int column, long value) {
    super(line, column);
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());

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
    return Imyhat.INTEGER;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
