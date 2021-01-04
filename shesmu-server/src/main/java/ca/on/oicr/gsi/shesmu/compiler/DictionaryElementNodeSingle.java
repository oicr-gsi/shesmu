package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DictionaryElementNodeSingle extends DictionaryElementNode {
  private static final Type A_MAP_TYPE = Type.getType(Map.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Method METHOD_MAP__PUT =
      new Method("put", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE, A_OBJECT_TYPE});
  private final ExpressionNode key;
  private final ExpressionNode value;

  public DictionaryElementNodeSingle(ExpressionNode key, ExpressionNode value) {
    this.key = key;
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    key.collectFreeVariables(names, predicate);
    value.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    key.collectPlugins(pluginFileNames);
    value.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer) {
    key.render(renderer);
    renderer.methodGen().box(key.type().apply(TO_ASM));
    value.render(renderer);
    renderer.methodGen().box(value.type().apply(TO_ASM));
    renderer.methodGen().invokeInterface(A_MAP_TYPE, METHOD_MAP__PUT);
    renderer.methodGen().pop();
  }

  @Override
  public String render(EcmaScriptRenderer renderer) {
    return String.format("[[%s, %s]]", key.renderEcma(renderer), value.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return key.resolve(defs, errorHandler) & value.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return key.resolveDefinitions(expressionCompilerServices, errorHandler)
        & value.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Pair<Imyhat, Imyhat> type() {
    return new Pair<>(key.type(), value.type());
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return key.typeCheck(errorHandler) & value.typeCheck(errorHandler);
  }

  @Override
  public void typeKeyError(Imyhat key, Consumer<String> errorHandler) {
    this.key.typeError(key, this.key.type(), errorHandler);
  }

  @Override
  public void typeValueError(Imyhat value, Consumer<String> errorHandler) {
    this.value.typeError(value, this.value.type(), errorHandler);
  }
}
