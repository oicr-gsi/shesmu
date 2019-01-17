package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.LONG_TYPE;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class StringNodeExpression extends StringNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);

  private static final Method METHOD_OBJECT__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {});
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
    } else {
      renderer.methodGen().invokeVirtual(A_OBJECT_TYPE, METHOD_OBJECT__TO_STRING);
      renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public String text() {
    return null;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      final Imyhat innerType = expression.type();
      if (Stream.of(Imyhat.INTEGER, Imyhat.DATE, Imyhat.PATH, Imyhat.STRING)
          .noneMatch(innerType::isSame)) {
        errorHandler.accept(
            String.format(
                "%d:%d: Cannot convert type %s to string in interpolation.",
                expression.line(), expression.column(), innerType.name()));
        return false;
      }
      return true;
    }
    return false;
  }
}
