package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public abstract class ObjectElementNode {

  public static Parser parse(Parser parser, Consumer<ObjectElementNode> output) {
    final var restResult = parser.whitespace().symbol("...");
    if (restResult.isGood()) {
      final var expression = new AtomicReference<ExpressionNode>();
      final var exceptions = new AtomicReference<List<String>>(List.of());
      var result =
          restResult.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
      var exceptionResult =
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
      final var name = new AtomicReference<String>();
      final var expression = new AtomicReference<ExpressionNode>();
      final var result =
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

  /** Add all free variable names to the set provided. */
  public abstract void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract Stream<Pair<String, Imyhat>> names();

  public abstract Stream<String> render(EcmaScriptRenderer renderer);

  public abstract void render(Renderer renderer, ToIntFunction<String> indexOf);

  public abstract Stream<String> renderConstant(EcmaScriptRenderer renderer);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all function plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  /** Perform type checking on this expression and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
