package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeContains extends ExpressionNode {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);

  private static final Method METHOD_SET__CONTAINS =
      new Method("contains", Type.BOOLEAN_TYPE, new Type[] {A_OBJECT_TYPE});

  private final ExpressionNode haystack;

  private final ExpressionNode needle;

  public ExpressionNodeContains(
      int line, int column, ExpressionNode needle, ExpressionNode haystack) {
    super(line, column);
    this.needle = needle;
    this.haystack = haystack;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    needle.collectFreeVariables(names, predicate);
    haystack.collectFreeVariables(names, predicate);
  }

  @Override
  public void render(Renderer renderer) {
    haystack.render(renderer);
    needle.render(renderer);
    renderer.mark(line());

    renderer.methodGen().box(needle.type().asmType());
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__CONTAINS);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return needle.resolve(defs, errorHandler) & haystack.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return needle.resolveFunctions(definedFunctions, errorHandler)
        & haystack.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final boolean ok = needle.typeCheck(errorHandler) & haystack.typeCheck(errorHandler);
    if (ok) {
      if (needle.type().asList().isSame(haystack.type())) {
        return true;
      }
      typeError(needle.type().asList().name(), haystack.type(), errorHandler);
      return false;
    }
    return ok;
  }
}
