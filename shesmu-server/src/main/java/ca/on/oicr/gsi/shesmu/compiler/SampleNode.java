package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Perform a subsampling operation in a <code>Subsample</code> clause in a <code>For</code>
 * expression
 */
public abstract class SampleNode
    implements JavaStreamBuilder.RenderSubsampler, EcmaStreamBuilder.RenderSubsampler {

  public enum Consumption {
    BAD,
    GREEDY,
    LIMITED
  }

  private static final Parser.ParseDispatch<SampleNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Squish",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new SampleNodeSquish(expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Fixed",
        (p, o) -> {
          final var limit = new AtomicReference<ExpressionNode>();
          final var condition = new AtomicReference<ExpressionNode>();
          final var result = p.whitespace().then(ExpressionNode::parse, limit::set).whitespace();
          if (result.isGood()) {
            final var conditionResult =
                result
                    .keyword("While")
                    .whitespace()
                    .then(ExpressionNode::parse, condition::set)
                    .whitespace();
            if (conditionResult.isGood()) {
              o.accept(new SampleNodeFixedWithCondition(limit.get(), condition.get()));
              return conditionResult;
            }
            o.accept(new SampleNodeFixed(limit.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<SampleNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public SampleNode() {}

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  /**
   * Check if there will be items left to subsample
   *
   * <p>Some operations consume all of the input and so operations that consume limited amounts of
   * input should not be after those that consume all of the input.
   */
  public abstract Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler);

  public abstract boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract boolean typeCheck(Imyhat type, Consumer<String> errorHandler);
}
