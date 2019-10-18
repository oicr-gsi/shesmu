package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** The arguments defined in “Monitor” clause. */
public final class MonitorArgumentNode {
  public static Parser parse(Parser input, Consumer<MonitorArgumentNode> output) {
    final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
    final AtomicReference<ExpressionNode> expression = new AtomicReference<>();

    final Parser result =
        input
            .whitespace()
            .then(DestructuredArgumentNode::parse, name::set)
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

  private final DestructuredArgumentNode name;

  public MonitorArgumentNode(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.expression = expression;
  }

  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(freeVariables, predicate);
  }

  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  /** Produce an error if the type of the expression is not as required */
  public boolean ensureType(Consumer<String> errorHandler) {
    if (name.isBlank()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Assignment in Monitor discards value.",
              expression.line(), expression.column()));
      return false;
    }
    return name.targets()
            .filter(
                target -> {
                  if (expression.type().isSame(Imyhat.STRING)) {
                    return false;
                  }
                  errorHandler.accept(
                      String.format(
                          "%d:%d: Expected argument “%s” to have type string, but got %s.",
                          line, column, target.name(), expression.type().name()));
                  return true;
                })
            .count()
        == 0;
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
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public Stream<Target> target() {
    return name.targets();
  }

  /** Perform type check on this argument's expression */
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler) && name.typeCheck(expression.type(), errorHandler);
  }
}
