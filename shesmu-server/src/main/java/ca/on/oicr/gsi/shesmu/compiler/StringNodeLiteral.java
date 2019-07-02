package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class StringNodeLiteral extends StringNode {

  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);

  private static final Method METHOD_STRINGBUILDER__APPEND__STR =
      new Method("append", A_STRINGBUILDER_TYPE, new Type[] {A_STRING_TYPE});

  private final String value;

  public StringNodeLiteral(String value) {
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public boolean isPassive() {
    return true;
  }

  @Override
  public void render(Renderer renderer) {
    renderer.methodGen().push(value);
    renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public String text() {
    return value;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
