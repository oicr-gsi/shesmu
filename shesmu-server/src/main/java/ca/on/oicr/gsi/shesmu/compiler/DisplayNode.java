package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class DisplayNode {
  private static final Parser.ParseDispatch<DisplayNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword("Bold", formatted("b"));
    DISPATCH.addKeyword("Italic", formatted("i"));
    DISPATCH.addKeyword("Mono", formatted("tt"));
    DISPATCH.addKeyword("Strike", formatted("s"));
    DISPATCH.addKeyword(
        "Link",
        (p, o) -> {
          final var link = new AtomicReference<ExpressionNode>();
          final var label = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, label::set)
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .then(ExpressionNode::parse, link::set);
          if (result.isGood()) {
            o.accept(new DisplayNodeHyperlink(label.get(), link.get()));
          }
          return result;
        });
    DISPATCH.addSymbol(
        "(",
        (p, o) ->
            p.list(i -> o.accept(new DisplayNodeList(i)), DisplayNode::parse, ',')
                .symbol(")")
                .whitespace());
    DISPATCH.addRaw(
        "text",
        (p, o) -> p.then(ExpressionNode::parse, e -> o.accept(new DisplayNodeExpression(e))));
  }

  private static Rule<DisplayNode> formatted(String code) {
    return (p, o) ->
        p.whitespace()
            .then(
                ExpressionNode::parse, e -> o.accept(new DisplayNodeFormattedExpression(e, code)));
  }

  public static Parser parse(Parser parser, Consumer<DisplayNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output);
  }

  public abstract String renderEcma(EcmaScriptRenderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
