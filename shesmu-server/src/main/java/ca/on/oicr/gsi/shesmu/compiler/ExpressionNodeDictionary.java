package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
  private final List<DictionaryElementNode> entries;
  private Imyhat key = Imyhat.BAD;
  private Imyhat value = Imyhat.BAD;

  public ExpressionNodeDictionary(int line, int column, List<DictionaryElementNode> entries) {
    super(line, column);
    this.entries = entries;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    for (final DictionaryElementNode entry : entries) {
      entry.collectFreeVariables(names, predicate);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final DictionaryElementNode entry : entries) {
      entry.collectPlugins(pluginFileNames);
    }
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return entries
        .stream()
        .map(entry -> entry.render(renderer))
        .collect(
            Collectors.joining(
                ", ",
                "$runtime.dictNew([",
                String.format(
                    "].flat(1), (a, b) => %s)", key.apply(EcmaScriptRenderer.COMPARATOR))));
  }

  @Override
  public void render(Renderer renderer) {
    renderer.loadImyhat(key.descriptor());
    renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__NEW_MAP);
    for (final DictionaryElementNode entry : entries) {
      renderer.methodGen().dup();
      entry.render(renderer);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return entries.stream().filter(e -> e.resolve(defs, errorHandler)).count() == entries.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return entries
            .stream()
            .filter(e -> e.resolveDefinitions(expressionCompilerServices, errorHandler))
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
    for (final DictionaryElementNode entry : entries) {
      if (!entry.typeCheck(errorHandler)) {
        ok = false;
      }
    }
    if (ok) {
      boolean first = true;
      for (final DictionaryElementNode entry : entries) {
        final Pair<Imyhat, Imyhat> type = entry.type();
        if (first) {
          key = type.first();
          value = type.second();
          first = false;
        } else {
          if (type.first().isSame(key)) {
            key = key.unify(type.first());
          } else {
            entry.typeKeyError(key, errorHandler);
            ok = false;
          }
          if (type.second().isSame(value)) {
            value = value.unify(type.second());
          } else {
            entry.typeValueError(value, errorHandler);
            ok = false;
          }
        }
      }
    }
    return ok;
  }
}
