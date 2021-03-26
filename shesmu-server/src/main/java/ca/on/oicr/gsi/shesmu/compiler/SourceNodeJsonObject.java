package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class SourceNodeJsonObject extends SourceNode {
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  public static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  public static final Imyhat FIELD_TUPLE = Imyhat.tuple(Imyhat.STRING, Imyhat.JSON);
  private static final Method METHOD_OPTIONAL__STREAM =
      new Method("stream", A_STREAM_TYPE, new Type[] {});
  private static final Method METHOD_RUNTIME_SUPPORT__JSON_FIELDS =
      new Method("jsonFields", A_STREAM_TYPE, new Type[] {Type.getType(JsonNode.class)});
  private static final Method METHOD_STREAM__FLATMAP =
      new Method("flatMap", A_STREAM_TYPE, new Type[] {Type.getType(Function.class)});
  private final ExpressionNode expression;
  private boolean lifted;

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
    if (lifted) {
      renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__STREAM);
      LambdaBuilder.pushStatic(
          renderer,
          A_RUNTIME_SUPPORT_TYPE,
          METHOD_RUNTIME_SUPPORT__JSON_FIELDS.getName(),
          LambdaBuilder.function(
              METHOD_RUNTIME_SUPPORT__JSON_FIELDS.getReturnType(),
              METHOD_RUNTIME_SUPPORT__JSON_FIELDS.getArgumentTypes()[0]));
      renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FLATMAP);
    } else {
      renderer
          .methodGen()
          .invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_RUNTIME_SUPPORT__JSON_FIELDS);
    }
    return renderer.buildStream(streamType());
  }

  @Override
  public EcmaStreamBuilder render(EcmaScriptRenderer renderer) {
    return renderer.buildStream(
        streamType(),
        lifted
            ? String.format(
                "$runtime.mapNullOrDefault(%s, $v => Object.entires(v), [])",
                expression.renderEcma(renderer))
            : String.format("Object.entries(%s)", expression.renderEcma(renderer)));
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
    final var type = expression.type();
    if (type.isSame(Imyhat.JSON)) {
      return true;
    } else if (type.isSame(Imyhat.JSON.asOptional())) {
      lifted = true;
      return true;
    } else {
      expression.typeError("json or json?", type, errorHandler);
      return false;
    }
  }
}
