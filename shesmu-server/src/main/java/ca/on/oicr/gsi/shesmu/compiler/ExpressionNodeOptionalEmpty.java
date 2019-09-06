package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeOptionalEmpty extends ExpressionNode {

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);

  private static final Method METHOD_OPTIONAL__EMPTY =
      new Method("empty", A_OPTIONAL_TYPE, new Type[] {});

  public ExpressionNodeOptionalEmpty(int line, int column) {
    super(line, column);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // No free variables.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());
    renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__EMPTY);
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
    return Imyhat.NOTHING;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
