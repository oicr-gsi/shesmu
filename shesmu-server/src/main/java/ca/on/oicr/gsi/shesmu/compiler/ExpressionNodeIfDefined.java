package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeIfDefined extends ExpressionNode {

  private final ExpressionNode falseExpression;
  private final List<DefinedCheckNode> tests;
  private final ExpressionNode trueExpression;
  private boolean isDefined;

  public ExpressionNodeIfDefined(
      int line,
      int column,
      List<DefinedCheckNode> tests,
      ExpressionNode trueExpression,
      ExpressionNode falseExpression) {
    super(line, column);
    this.tests = tests;
    this.trueExpression = trueExpression;
    this.falseExpression = falseExpression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    (isDefined ? trueExpression : falseExpression).collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    (isDefined ? trueExpression : falseExpression).collectPlugins(pluginFileNames);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return (isDefined ? trueExpression : falseExpression).renderEcma(renderer);
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());
    (isDefined ? trueExpression : falseExpression).render(renderer);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    if (isDefined) {
      isDefined = tests.stream().allMatch(p -> p.check(defs));
    }
    // At this point, we've committed to one path, so only do resolution in one expression.
    return (isDefined ? trueExpression : falseExpression).resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    // Since we have to check for functions now and constants later, if we think it is defined, we
    // will still resolve functions on both paths and we might give up on the "true" path later.
    isDefined = tests.stream().allMatch(p -> p.check(expressionCompilerServices));
    return (!isDefined
            || trueExpression.resolveDefinitions(expressionCompilerServices, errorHandler))
        & falseExpression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return (isDefined ? trueExpression : falseExpression).type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return (isDefined ? trueExpression : falseExpression).typeCheck(errorHandler);
  }
}
