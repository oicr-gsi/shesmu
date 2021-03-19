package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public abstract class VariableTagNode {

  private static final ParseDispatch<VariableTagNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Tags",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new VariableTagNodeMultiple(expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Tag",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new VariableTagNodeSingle(expression.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<VariableTagNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  private final ExpressionNode expression;

  protected VariableTagNode(ExpressionNode expression) {
    this.expression = expression;
  }

  public final void collectFreeVariables(Set<String> names, Predicate<Flavour> needsCapture) {
    expression.collectFreeVariables(names, needsCapture);
  }

  public final void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  protected final void render(Renderer renderer) {
    expression.render(renderer);
  }

  public abstract Optional<IntConsumer> renderDynamicSize(Renderer renderer);

  public abstract int renderStaticTag(Renderer renderer, int tagIndex);

  protected abstract Imyhat requiredType();

  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  public final boolean resolveDefinitions(
      ExpressionCompilerServices services, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(services, errorHandler);
  }

  public abstract int staticSize();

  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type().isSame(requiredType())) {
        return true;
      }
      expression.typeError(requiredType(), expression.type(), errorHandler);
    }
    return false;
  }
}
