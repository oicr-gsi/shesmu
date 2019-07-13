package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Perform a subsampling operation in a <tt>Subsample<tt> clause in a <tt>For</tt> expression */
public abstract class SampleNode implements JavaStreamBuilder.RenderSubsampler {

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
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new SampleNodeSquish(expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Fixed",
        (p, o) -> {
          final AtomicReference<ExpressionNode> limit = new AtomicReference<>();
          final AtomicReference<ExpressionNode> condition = new AtomicReference<>();
          final Parser result = p.whitespace().then(ExpressionNode::parse, limit::set).whitespace();
          if (result.isGood()) {
            final Parser conditionResult =
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
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler);

  public abstract boolean typeCheck(Imyhat type, Consumer<String> errorHandler);
}
