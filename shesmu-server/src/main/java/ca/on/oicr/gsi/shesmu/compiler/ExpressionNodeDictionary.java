package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class ExpressionNodeDictionary extends ExpressionNode {
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Method METHOD_IMYHAT__NEW_MAP =
      new Method("newMap", A_MAP_TYPE, new Type[0]);
  private static final Method METHOD_MAP__PUT =
      new Method("put", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  private final List<Pair<ExpressionNode, ExpressionNode>> entries;
  private Imyhat key = Imyhat.BAD;
  private Imyhat value = Imyhat.BAD;

  public ExpressionNodeDictionary(
      int line, int column, List<Pair<ExpressionNode, ExpressionNode>> entries) {
    super(line, column);
    this.entries = entries;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final Pair<ExpressionNode, ExpressionNode> entry : entries) {
      entry.first().collectFreeVariables(names, predicate);
      entry.second().collectFreeVariables(names, predicate);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final Pair<ExpressionNode, ExpressionNode> entry : entries) {
      entry.first().collectPlugins(pluginFileNames);
      entry.second().collectPlugins(pluginFileNames);
    }
  }

  @Override
  public void render(Renderer renderer) {
    renderer.loadImyhat(key.descriptor());
    renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_MAP);
    for (final Pair<ExpressionNode, ExpressionNode> entry : entries) {
      renderer.methodGen().dup();
      entry.first().render(renderer);
      renderer.methodGen().box(key.apply(TO_ASM));
      entry.second().render(renderer);
      renderer.methodGen().box(value.apply(TO_ASM));
      renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__PUT);
      renderer.methodGen().pop();
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return entries
            .stream()
            .filter(
                e -> e.first().resolve(defs, errorHandler) & e.second().resolve(defs, errorHandler))
            .count()
        == entries.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return entries
            .stream()
            .filter(
                e ->
                    e.first().resolveDefinitions(expressionCompilerServices, errorHandler)
                        & e.second().resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == entries.size();
  }

  @Override
  public Imyhat type() {
    return Imyhat.dictionary(key, value);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final Pair<ExpressionNode, ExpressionNode> entry : entries) {
      if (!entry.first().typeCheck(errorHandler) | !entry.second().typeCheck(errorHandler)) {
        ok = false;
      }
    }
    if (ok) {
      boolean first = true;
      for (final Pair<ExpressionNode, ExpressionNode> entry : entries) {
        if (first) {
          key = entry.first().type();
          value = entry.second().type();
          first = false;
        } else {
          if (entry.first().type().isSame(key)) {
            key = key.unify(entry.first().type());
          } else {
            entry.first().typeError(key, entry.first().type(), errorHandler);
            ok = false;
          }
          if (entry.second().type().isSame(value)) {
            value = value.unify(entry.first().type());
          } else {
            entry.second().typeError(value, entry.second().type(), errorHandler);
            ok = false;
          }
        }
      }
    }
    return ok;
  }
}
