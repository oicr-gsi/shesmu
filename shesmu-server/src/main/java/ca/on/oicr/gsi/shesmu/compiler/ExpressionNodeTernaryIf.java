package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ExpressionNodeTernaryIf extends ExpressionNode {

  private final ExpressionNode falseExpression;
  private final ExpressionNode testExpression;
  private final ExpressionNode trueExpression;
  private Imyhat type = Imyhat.BAD;

  public ExpressionNodeTernaryIf(
      int line,
      int column,
      ExpressionNode testExpression,
      ExpressionNode trueExpression,
      ExpressionNode falseExpression) {
    super(line, column);
    this.testExpression = testExpression;
    this.trueExpression = trueExpression;
    this.falseExpression = falseExpression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    testExpression.collectFreeVariables(names, predicate);
    trueExpression.collectFreeVariables(names, predicate);
    falseExpression.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    testExpression.collectPlugins(pluginFileNames);
    trueExpression.collectPlugins(pluginFileNames);
    falseExpression.collectPlugins(pluginFileNames);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final var result = renderer.newLet();
    renderer.conditional(
        testExpression.renderEcma(renderer),
        whenTrue ->
            whenTrue.statement(
                String.format("%s = %s", result, trueExpression.renderEcma(whenTrue))),
        whenFalse ->
            whenFalse.statement(
                String.format("%s = %s", result, falseExpression.renderEcma(whenFalse))));
    return result;
  }

  @Override
  public void render(Renderer renderer) {
    testExpression.render(renderer);
    renderer.mark(line());

    final var end = renderer.methodGen().newLabel();
    final var truePath = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.NE, truePath);
    falseExpression.render(renderer);
    renderer.methodGen().goTo(end);
    renderer.methodGen().mark(truePath);
    trueExpression.render(renderer);
    renderer.methodGen().mark(end);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return testExpression.resolve(defs, errorHandler)
        & trueExpression.resolve(defs, errorHandler)
        & falseExpression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return testExpression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & trueExpression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & falseExpression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var testOk = testExpression.typeCheck(errorHandler);
    if (testOk) {
      testOk = testExpression.type().isSame(Imyhat.BOOLEAN);
      if (!testOk) {
        typeError(Imyhat.BOOLEAN, testExpression.type(), errorHandler);
      }
    }
    var resultOk = trueExpression.typeCheck(errorHandler) & falseExpression.typeCheck(errorHandler);
    if (resultOk) {
      resultOk = trueExpression.type().isSame(falseExpression.type());
      if (!resultOk) {
        typeError(trueExpression.type(), falseExpression.type(), errorHandler);
      } else {
        type = trueExpression.type().unify(falseExpression.type());
      }
    }
    return testOk & resultOk;
  }
}
