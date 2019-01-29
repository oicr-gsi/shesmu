package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ExpressionNodeNegate extends ExpressionNode {
  private final ExpressionNode inner;

  public ExpressionNodeNegate(int line, int column, ExpressionNode inner) {
    super(line, column);
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    inner.collectFreeVariables(names, predicate);
  }

  @Override
  public void render(Renderer renderer) {
    inner.render(renderer);
    renderer.methodGen().math(GeneratorAdapter.NEG, Type.LONG_TYPE);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return inner.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return inner.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.INTEGER;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = inner.typeCheck(errorHandler);
    if (ok) {
      ok = inner.type().isSame(Imyhat.INTEGER);
      if (!ok) {
        typeError("integer", inner.type(), errorHandler);
      }
    }
    return ok;
  }
}
