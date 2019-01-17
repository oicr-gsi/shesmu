package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** The arguments defined in “Monitor” clause. */
public final class MonitorArgumentNode {
  public static Parser parse(Parser input, Consumer<MonitorArgumentNode> output) {
    final AtomicReference<String> name = new AtomicReference<>();
    final AtomicReference<ExpressionNode> expression = new AtomicReference<>();

    final Parser result =
        input
            .whitespace()
            .identifier(name::set)
            .whitespace()
            .keyword("=")
            .whitespace()
            .then(ExpressionNode::parse, expression::set)
            .whitespace();
    if (result.isGood()) {
      output.accept(
          new MonitorArgumentNode(input.line(), input.column(), name.get(), expression.get()));
    }
    return result;
  }

  private final int column;
  private final ExpressionNode expression;
  private final int line;

  private final String name;

  public MonitorArgumentNode(int line, int column, String name, ExpressionNode expression) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.expression = expression;
  }

  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(freeVariables, predicate);
  }

  /**
   * Produce an error if the type of the expression is not as required
   *
   * @param targetType the required type
   */
  public boolean ensureType(Consumer<String> errorHandler) {
    final boolean ok = expression.type().isSame(Imyhat.STRING);
    if (!ok) {
      errorHandler.accept(
          String.format(
              "%d:%d: Expected argument “%s” to have type string, but got %s.",
              line, column, name, expression.type().name()));
    }
    return ok;
  }

  /** The argument name */
  public String name() {
    return name;
  }

  /** Generate bytecode for this argument's value */
  public void render(Renderer renderer) {
    expression.render(renderer);
  }

  /** Resolve variables in the expression of this argument */
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  /** Resolve functions in this argument */
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  /** Perform type check on this argument's expression */
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
