package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodePathLiteral extends ExpressionNode {
  private final String path;
  private static final Type A_RUNTIME_SUPPORT = Type.getType(RuntimeSupport.class);
  private static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  private static final Type A_PATHS_TYPE = Type.getType(Paths.class);
  private static final Method METHOD_PATHS__GET =
      new Method(
          "get",
          Type.getType(Path.class),
          new Type[] {Type.getType(String.class), A_STRING_ARRAY_TYPE});

  public ExpressionNodePathLiteral(int line, int column, String path) {
    super(line, column);
    this.path = path;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // No free variables.
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().push(path);
    renderer.methodGen().getStatic(A_RUNTIME_SUPPORT, "EMPTY", A_STRING_ARRAY_TYPE);
    renderer.methodGen().invokeStatic(A_PATHS_TYPE, METHOD_PATHS__GET);
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
    return Imyhat.PATH;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
