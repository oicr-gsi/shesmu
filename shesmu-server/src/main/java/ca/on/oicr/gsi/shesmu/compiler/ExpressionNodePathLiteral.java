package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.PathParser;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import tools.jackson.core.JacksonException;

public class ExpressionNodePathLiteral extends ExpressionNode {
  private final String path;
  private static final Type A_PATH_PARSER_TYPE = Type.getType(PathParser.class);
  private static final Method METHOD_PATH_PARSER__PARSE =
      new Method("parse", Type.getType(Path.class), new Type[] {Type.getType(String.class)});

  public ExpressionNodePathLiteral(int line, int column, String path) {
    super(line, column);
    this.path = path;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // No free variables.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    try {
      return RuntimeSupport.MAPPER.writeValueAsString(path);
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().push(path);
    renderer.methodGen().invokeStatic(A_PATH_PARSER_TYPE, METHOD_PATH_PARSER__PARSE);
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
    return Imyhat.PATH;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
