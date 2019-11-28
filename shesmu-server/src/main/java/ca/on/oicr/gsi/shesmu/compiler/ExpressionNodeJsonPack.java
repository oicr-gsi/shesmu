package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.JsonWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeJsonPack extends ExpressionNode {
  private static final Type A_JSON_WRAPPER_TYPE = Type.getType(JsonWrapper.class);
  private static final Method JSON_CONVERTER__CONVERT =
      new Method(
          "convert",
          Type.getType(JsonNode.class),
          new Type[] {Type.getType(Imyhat.class), Type.getType(Object.class)});
  private final ExpressionNode expression;

  public ExpressionNodeJsonPack(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    renderer.loadImyhat(expression.type().descriptor());
    expression.render(renderer);
    renderer.methodGen().valueOf(expression.type().apply(TO_ASM));
    renderer.methodGen().invokeStatic(A_JSON_WRAPPER_TYPE, JSON_CONVERTER__CONVERT);
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
  public Imyhat type() {
    return Imyhat.JSON;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
