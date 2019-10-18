package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeOptionalOf extends ExpressionNode {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);

  private static final Method METHOD_OPTIONAL__OF =
      new Method("of", A_OPTIONAL_TYPE, new Type[] {A_OBJECT_TYPE});

  private final ExpressionNode item;

  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeOptionalOf(int line, int column, ExpressionNode item) {
    super(line, column);
    this.item = item;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    item.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    item.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    item.render(renderer);
    renderer.mark(line());
    renderer.methodGen().box(type.apply(TypeUtils.TO_ASM));
    renderer.methodGen().invokeStatic(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OF);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return item.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return item.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type.asOptional();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = item.typeCheck(errorHandler);
    if (ok) {
      type = item.type();
      if (type.isSame(type.asOptional())) {
        item.typeError("non-optional", item.type(), errorHandler);
        ok = false;
      }
    }
    return ok;
  }
}
