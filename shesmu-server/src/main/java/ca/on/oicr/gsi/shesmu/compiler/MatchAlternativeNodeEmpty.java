package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public class MatchAlternativeNodeEmpty extends MatchAlternativeNode {

  private static final Type A_ILLEGAL_STATE_EXCEPTION_TYPE =
      Type.getType(IllegalStateException.class);

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.

  }

  @Override
  public void render(Renderer renderer, Label end, int local) {
    renderer
        .methodGen()
        .throwException(
            A_ILLEGAL_STATE_EXCEPTION_TYPE,
            "Unsupported algebraic value in “Match” with no alternative.");
  }

  @Override
  public String render(EcmaScriptRenderer renderer, String original) {
    renderer.statement("throw new Error(\"Unsupported algebraic value in “Match” with no alternative.\")");
    return "null";
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat typeCheck(
      int line,
      int column,
      Imyhat resultType,
      Map<String, Imyhat> remainingBranches,
      Consumer<String> errorHandler) {
    if (remainingBranches.isEmpty()) {
      return resultType;
    } else {

      errorHandler.accept(
          String.format(
              "%d:%d: Branches are not exhaustive. Add “Else” or %s.",
              line, column, String.join(" and ", remainingBranches.keySet())));
      return Imyhat.BAD;
    }
  }
}
