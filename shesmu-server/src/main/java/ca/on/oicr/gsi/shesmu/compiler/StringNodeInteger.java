package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class StringNodeInteger extends StringNode {

  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);
  private static final Method METHOD_FORMAT_NUMBER =
      new Method(
          "appendFormatted",
          A_STRINGBUILDER_TYPE,
          new Type[] {A_STRINGBUILDER_TYPE, LONG_TYPE, INT_TYPE});
  private final int column;

  private final ExpressionNode expression;

  private final int line;

  private final int width;

  public StringNodeInteger(int line, int column, ExpressionNode expression, int width) {
    this.line = line;
    this.column = column;
    this.expression = expression;
    this.width = width;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isPassive() {
    return false;
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.methodGen().push(width);
    renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_FORMAT_NUMBER);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format("$runtime.formatNumber(%s, %d)", expression.renderEcma(renderer), width);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public String text() {
    return null;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = expression.typeCheck(errorHandler);
    if (ok) {
      ok = expression.type().isSame(Imyhat.INTEGER);
      if (!ok) {
        errorHandler.accept(
            String.format(
                "%d:%d: Expected integer in padded interpolation, but got %s.",
                line, column, expression.type()));
      }
    }
    return ok;
  }
}
