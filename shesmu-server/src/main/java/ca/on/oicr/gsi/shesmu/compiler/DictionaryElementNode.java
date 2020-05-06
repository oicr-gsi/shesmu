package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class DictionaryElementNode {

  public static Parser parse(Parser parser, Consumer<DictionaryElementNode> output) {
    final AtomicReference<DictionaryElementNode> value = new AtomicReference<>();

    final Parser restResult = parser.whitespace().symbol("...");
    if (restResult.isGood()) {
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      Parser result =
          restResult.whitespace().then(ExpressionNode::parse, expression::set).whitespace();

      if (result.isGood()) {
        output.accept(new DictionaryElementNodeRest(expression.get()));
      }
      return result;
    } else {
      final AtomicReference<ExpressionNode> name = new AtomicReference<>();
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final Parser result =
          parser
              .whitespace()
              .then(ExpressionNode::parse, name::set)
              .whitespace()
              .symbol("=")
              .whitespace()
              .then(ExpressionNode::parse, expression::set)
              .whitespace();
      if (result.isGood()) {
        output.accept(new DictionaryElementNodeSingle(name.get(), expression.get()));
      }
      return result;
    }
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract void render(Renderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Pair<Imyhat, Imyhat> type();

  public abstract boolean typeCheck(Consumer<String> errorHandler);

  public abstract void typeKeyError(Imyhat key, Consumer<String> errorHandler);

  public abstract void typeValueError(Imyhat value, Consumer<String> errorHandler);
}
