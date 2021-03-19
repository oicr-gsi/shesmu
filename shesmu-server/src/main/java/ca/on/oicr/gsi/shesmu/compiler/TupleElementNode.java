package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class TupleElementNode {
  public static Parser parse(Parser parser, Consumer<TupleElementNode> output) {
    final var restResult = parser.whitespace().symbol("...");
    if (restResult.isGood()) {
      final var expression = new AtomicReference<ExpressionNode>();
      final var result =
          restResult.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
      if (result.isGood()) {
        output.accept(new TupleElementNodeRest(expression.get()));
      }
      return result;
    } else {
      final var expression = new AtomicReference<ExpressionNode>();
      final var result =
          parser.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
      if (result.isGood()) {
        output.accept(new TupleElementNodeSingle(expression.get()));
      }
      return result;
    }
  }

  protected final ExpressionNode expression;

  protected TupleElementNode(ExpressionNode expression) {
    this.expression = expression;
  }

  /** Add all free variable names to the set provided. */
  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  public abstract int render(Renderer renderer, int start);

  public abstract String render(EcmaScriptRenderer renderer);

  /** Resolve all variable plugins in this expression and its children. */
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  /** Resolve all function plugins in this expression */
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  /** Perform type checking on this expression and its children. */
  public final boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler) && typeCheckExtra(errorHandler);
  }

  public abstract boolean typeCheckExtra(Consumer<String> errorHandler);

  public abstract Stream<Imyhat> types();
}
