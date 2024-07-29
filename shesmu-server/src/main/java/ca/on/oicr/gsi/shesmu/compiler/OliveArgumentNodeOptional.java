package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.commons.GeneratorAdapter;

/** The arguments defined in the “With” section of a “Run” olive. */
public final class OliveArgumentNodeOptional extends OliveArgumentNode {

  private final ExpressionNode condition;
  private final ExpressionNode expression;

  public OliveArgumentNodeOptional(
      int line,
      int column,
      DestructuredArgumentNode name,
      ExpressionNode condition,
      ExpressionNode expression) {
    super(line, column, name);
    this.condition = condition;
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(freeVariables, predicate);
    condition.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
    condition.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isConditional() {
    return true;
  }

  /** Generate bytecode for this argument's value */
  @Override
  public void render(Renderer renderer, int action) {
    condition.render(renderer);
    renderer.mark(line);
    final var end = renderer.methodGen().newLabel();
    renderer.methodGen().ifZCmp(GeneratorAdapter.EQ, end);
    storeAll(renderer, action, expression::render);
    renderer.methodGen().mark(end);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final var result = renderer.newLet("{}");
    renderer.conditional(
        condition.renderEcma(renderer),
        ecmaScriptRenderer ->
            ecmaScriptRenderer.statement(
                String.format(
                    "%s = { %s }",
                    result,
                    storeAll(ecmaScriptRenderer, expression.renderEcma(ecmaScriptRenderer)))));
    return String.format("...%s", result);
  }

  /** Resolve variables in the expression of this argument */
  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler) & condition.resolve(defs, errorHandler);
  }

  /** Resolve functions in this argument */
  @Override
  public boolean resolveExtraFunctions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler)
        & condition.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return expression.type();
  }

  /** Perform type check on this argument's expression */
  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = expression.typeCheck(errorHandler) & condition.typeCheck(errorHandler);
    if (ok && !Imyhat.BOOLEAN.isSame(condition.type())) {
      errorHandler.accept(
          String.format(
              "%d:%d: Condition for argument “%s” must be boolean, but got %s.",
              line, column, name, condition.type().name()));
      ok = false;
    }
    return ok;
  }
}
