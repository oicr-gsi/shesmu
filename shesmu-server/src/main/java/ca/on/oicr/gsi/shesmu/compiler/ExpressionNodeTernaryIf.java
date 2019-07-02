package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

public class ExpressionNodeTernaryIf extends ExpressionNode {

  private final ExpressionNode falseExpression;
  private final ExpressionNode testExpression;
  private final ExpressionNode trueExpression;

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
  public void render(Renderer renderer) {
    testExpression.render(renderer);
    renderer.mark(line());

    final Label end = renderer.methodGen().newLabel();
    final Label truePath = renderer.methodGen().newLabel();
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
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return testExpression.resolveFunctions(definedFunctions, errorHandler)
        & trueExpression.resolveFunctions(definedFunctions, errorHandler)
        & falseExpression.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return trueExpression.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean testOk = testExpression.typeCheck(errorHandler);
    if (testOk) {
      testOk = testExpression.type().isSame(Imyhat.BOOLEAN);
      if (!testOk) {
        typeError("boolean", testExpression.type(), errorHandler);
      }
    }
    boolean resultOk =
        trueExpression.typeCheck(errorHandler) & falseExpression.typeCheck(errorHandler);
    if (resultOk) {
      resultOk = trueExpression.type().isSame(falseExpression.type());
      if (!resultOk) {
        typeError(trueExpression.type().name(), falseExpression.type(), errorHandler);
      }
    }
    return testOk & resultOk;
  }
}
