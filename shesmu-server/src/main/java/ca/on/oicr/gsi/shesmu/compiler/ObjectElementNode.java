package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public abstract class ObjectElementNode {
  protected final ExpressionNode expression;

  protected ObjectElementNode(ExpressionNode expression) {
    this.expression = expression;
  }

  /** Add all free variable names to the set provided. */
  public final void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  public static Parser parse(Parser parser, Consumer<ObjectElementNode> output) {
    final Parser restResult = parser.whitespace().symbol("...");
    if (restResult.isGood()) {
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final AtomicReference<List<String>> exceptions =
          new AtomicReference<>(Collections.emptyList());
      Parser result =
          restResult.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
      Parser exceptionResult =
          result
              .keyword("Without")
              .whitespace()
              .list(exceptions::set, (p, o) -> p.whitespace().identifier(o).whitespace());
      if (exceptionResult.isGood()) {
        result = exceptionResult;
      }
      if (result.isGood()) {
        output.accept(new ObjectElementNodeRest(expression.get(), exceptions.get()));
      }
      return result;
    } else {
      final AtomicReference<String> name = new AtomicReference<>();
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final Parser result =
          parser
              .whitespace()
              .identifier(name::set)
              .whitespace()
              .symbol("=")
              .whitespace()
              .then(ExpressionNode::parse, expression::set)
              .whitespace();
      if (result.isGood()) {
        output.accept(new ObjectElementNodeSingle(name.get(), expression.get()));
      }
      return result;
    }
  }

  public abstract Stream<Pair<String, Imyhat>> names();

  public abstract Stream<String> render(EcmaScriptRenderer renderer);

  public abstract void render(Renderer renderer, ToIntFunction<String> indexOf);

  public abstract Stream<String> renderConstant(EcmaScriptRenderer renderer);

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
