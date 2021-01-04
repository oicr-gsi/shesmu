package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.DictionaryImyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DictionaryElementNodeRest extends DictionaryElementNode {

  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Method METHOD_MAP__PUT_ALL =
      new Method("putAll", Type.VOID_TYPE, new Type[] {A_MAP_TYPE});
  private final ExpressionNode expression;
  private Imyhat key = Imyhat.BAD;
  private Imyhat value = Imyhat.BAD;

  public DictionaryElementNodeRest(ExpressionNode expression) {
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
  public void render(Renderer renderer) {
    expression.render(renderer);
    renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__PUT_ALL);
  }

  @Override
  public String render(EcmaScriptRenderer renderer) {
    return expression.renderEcma(renderer);
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
  public Pair<Imyhat, Imyhat> type() {
    return new Pair<>(key, value);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type() instanceof DictionaryImyhat) {
        final DictionaryImyhat dictType = (DictionaryImyhat) expression.type();
        key = dictType.key();
        value = dictType.value();
        return true;
      } else {
        expression.typeError("dictionary", expression.type(), errorHandler);
        return false;
      }
    } else {
      return false;
    }
  }

  @Override
  public void typeKeyError(Imyhat key, Consumer<String> errorHandler) {
    errorHandler.accept(
        String.format(
            "%d:%d: Expected %s for dictionary key, but existing dictionary has key %s.",
            expression.line(), expression.column(), key.name(), this.key.name()));
  }

  @Override
  public void typeValueError(Imyhat value, Consumer<String> errorHandler) {
    errorHandler.accept(
        String.format(
            "%d:%d: Expected %s for dictionary value, but existing dictionary has value %s.",
            expression.line(), expression.column(), value.name(), this.value.name()));
  }
}
