package ca.on.oicr.gsi.shesmu.compiler;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class ObjectElementNodeBaseExpression extends ObjectElementNode {
  protected final ExpressionNode expression;

  protected ObjectElementNodeBaseExpression(ExpressionNode expression) {
    this.expression = expression;
  }

  /** Add all free variable names to the set provided. */
  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  /** Resolve all variable plugins in this expression and its children. */
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  /** Resolve all function plugins in this expression */
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public abstract boolean typeCheckExtra(Consumer<String> errorHandler);

  /** Perform type checking on this expression and its children. */
  public final boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler) && typeCheckExtra(errorHandler);
  }
}
