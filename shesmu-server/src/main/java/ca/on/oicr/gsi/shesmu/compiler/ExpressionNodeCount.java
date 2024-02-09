package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ListImyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeCount extends ExpressionNode {

  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_SET__SIZE = new Method("size", Type.INT_TYPE, new Type[] {});
  private final ExpressionNode inner;

  public ExpressionNodeCount(int line, int column, ExpressionNode inner) {
    super(line, column);
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    inner.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    inner.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    inner.render(renderer);
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__SIZE);
    renderer.methodGen().cast(Type.INT_TYPE, Type.LONG_TYPE);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return "(" + inner.renderEcma(renderer) + ").length";
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return inner.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.INTEGER;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = inner.typeCheck(errorHandler);
    if (ok) {
      ok = inner.type() instanceof ListImyhat || inner.type().equals(Imyhat.EMPTY);
      if (!ok) {
        typeError("list", inner.type(), errorHandler);
      }
    }
    return ok;
  }
}
