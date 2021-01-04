package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.DOUBLE_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class StringNodeExpression extends StringNode {

  public static boolean canBeConverted(Imyhat type) {
    return Stream.of(
            Imyhat.FLOAT, Imyhat.INTEGER, Imyhat.DATE, Imyhat.JSON, Imyhat.PATH, Imyhat.STRING)
        .anyMatch(type::isSame);
  }

  private static final Type A_OBJECT_MAPPER_TYPE = Type.getType(ObjectMapper.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method METHOD_OBJECT_MAPPER__WRITE_VALUE_AS_STRING =
      new Method("writeValueAsString", A_STRING_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_OBJECT__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {});
  private static final Method METHOD_STRINGBUILDER__APPEND__DOUBLE =
      new Method("append", A_STRINGBUILDER_TYPE, new Type[] {DOUBLE_TYPE});
  private static final Method METHOD_STRINGBUILDER__APPEND__LONG =
      new Method("append", A_STRINGBUILDER_TYPE, new Type[] {LONG_TYPE});
  private static final Method METHOD_STRINGBUILDER__APPEND__STR =
      new Method("append", A_STRINGBUILDER_TYPE, new Type[] {A_STRING_TYPE});
  private final ExpressionNode expression;

  public StringNodeExpression(ExpressionNode expression) {
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
  public boolean isPassive() {
    return false;
  }

  @Override
  public void render(Renderer renderer) {
    expression.render(renderer);
    if (expression.type().isSame(Imyhat.STRING)) {
      renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
    } else if (expression.type().isSame(Imyhat.INTEGER)) {
      renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__LONG);
    } else if (expression.type().isSame(Imyhat.FLOAT)) {
      renderer
          .methodGen()
          .invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__DOUBLE);
    } else if (expression.type().isSame(Imyhat.JSON)) {
      renderer.methodGen().getStatic(A_RUNTIME_SUPPORT_TYPE, "MAPPER", A_OBJECT_MAPPER_TYPE);
      renderer.methodGen().swap();
      renderer
          .methodGen()
          .invokeVirtual(A_OBJECT_MAPPER_TYPE, METHOD_OBJECT_MAPPER__WRITE_VALUE_AS_STRING);
      renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
    } else {
      renderer.methodGen().invokeVirtual(A_OBJECT_TYPE, METHOD_OBJECT__TO_STRING);
      renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
    }
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    if (expression.type().isSame(Imyhat.STRING)) {
      return expression.renderEcma(renderer);
    } else if (expression.type().isSame(Imyhat.JSON)) {
      return "JSON.stringify(" + expression.renderEcma(renderer) + ")";
    } else {
      return expression.renderEcma(renderer) + ".toString()";
    }
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
    if (expression.typeCheck(errorHandler)) {
      final Imyhat innerType = expression.type();
      if (canBeConverted(innerType)) {
        return true;
      }
      errorHandler.accept(
          String.format(
              "%d:%d: Cannot convert type %s to string in interpolation.",
              expression.line(), expression.column(), innerType.name()));
      return false;
    }
    return false;
  }
}
