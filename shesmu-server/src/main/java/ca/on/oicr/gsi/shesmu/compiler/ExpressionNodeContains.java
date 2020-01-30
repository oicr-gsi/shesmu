package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
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
  public void collectPlugins(Set<Path> pluginFileNames) {
    haystack.collectPlugins(pluginFileNames);
    needle.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    haystack.render(renderer);
    needle.render(renderer);
    renderer.mark(line());

    renderer.methodGen().valueOf(needle.type().apply(TypeUtils.TO_ASM));
    renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__CONTAINS);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return needle.resolve(defs, errorHandler) & haystack.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return needle.resolveDefinitions(expressionCompilerServices, errorHandler)
        & haystack.resolveDefinitions(expressionCompilerServices, errorHandler);
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
      typeError(needle.type().asList(), haystack.type(), errorHandler);
      return false;
    }
    return ok;
  }
}
