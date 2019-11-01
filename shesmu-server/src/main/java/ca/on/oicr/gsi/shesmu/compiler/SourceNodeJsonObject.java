package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeJsonObject extends SourceNode {
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  public static final Imyhat FIELD_TUPLE = Imyhat.tuple(Imyhat.STRING, Imyhat.JSON);
  private static final Method METHOD_RUNTIME_SUPPORT__JSON_FIELDS =
      new Method(
          "jsonFields", Type.getType(Stream.class), new Type[] {Type.getType(JsonNode.class)});

  private final ExpressionNode expression;

  public SourceNodeJsonObject(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
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
  public Ordering ordering() {
    return Ordering.RANDOM;
  }

  @Override
  public JavaStreamBuilder render(Renderer renderer) {
    expression.render(renderer);
    renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JSON_FIELDS);
    final JavaStreamBuilder builder = renderer.buildStream(streamType());
    return builder;
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
  public Imyhat streamType() {
    return FIELD_TUPLE;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!expression.typeCheck(errorHandler)) {
      return false;
    }
    final Imyhat type = expression.type();
    if (type.isSame(Imyhat.JSON)) {
      return true;
    } else {
      expression.typeError("list or json or optional", type, errorHandler);
      return false;
    }
  }
}
