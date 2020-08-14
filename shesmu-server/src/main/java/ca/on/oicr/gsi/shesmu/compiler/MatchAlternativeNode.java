package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.Label;

public abstract class MatchAlternativeNode {

  private static final ParseDispatch<MatchAlternativeNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Else",
        (p, o) -> {
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse0, expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new MatchAlternativeNodeElse(expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Remainder",
        (p, o) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .identifier(name::set)
                  .whitespace()
                  .symbol(")")
                  .whitespace()
                  .then(ExpressionNode::parse0, expression::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new MatchAlternativeNodeRemainder(name.get(), expression.get()));
          }
          return result;
        });

    DISPATCH.addRaw(
        "nothing",
        (p, o) -> {
          o.accept(new MatchAlternativeNodeEmpty());
          return p;
        });
  }

  public static Parser parse(Parser input, Consumer<MatchAlternativeNode> output) {
    return input.whitespace().dispatch(DISPATCH, output);
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract void render(Renderer renderer, Label end, int local);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Imyhat typeCheck(
      int line,
      int column,
      Imyhat resultType,
      Map<String, Imyhat> remainingBranches,
      Consumer<String> errorHandler);
}
