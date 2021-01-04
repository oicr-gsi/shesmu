package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;

public class MatchAlternativeNodeElse extends MatchAlternativeNode {

  private final ExpressionNode expression;

  public MatchAlternativeNodeElse(ExpressionNode expression) {
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
  public void render(Renderer renderer, Label end, int local) {
    expression.render(renderer);
    renderer.methodGen().goTo(end);
  }

  @Override
  public String render(EcmaScriptRenderer renderer, String original) {
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
  public Imyhat typeCheck(
      int line,
      int column,
      Imyhat resultType,
      Map<String, Imyhat> remainingBranches,
      Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (expression.type().isSame(resultType)) {
        return expression.type().unify(resultType);
      } else {
        expression.typeError(resultType, expression.type(), errorHandler);
      }
    }
    return Imyhat.BAD;
  }
}
