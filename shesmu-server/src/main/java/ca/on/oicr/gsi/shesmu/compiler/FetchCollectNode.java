package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class FetchCollectNode {
  private static final ParseDispatch<FetchCollectNode> DISPATCHER = new ParseDispatch<>();

  static {
    DISPATCHER.addKeyword(
        "List",
        (p, o) -> p.whitespace().then(FetchNode::parse, FetchCollectNodeList::new).whitespace());
    DISPATCHER.addKeyword(
        "Flatten",
        (p, o) ->
            p.whitespace()
                .then(
                    FetchNode::parse,
                    inner -> new FetchCollectNodeFlatten(p.line(), p.column(), inner))
                .whitespace());
    DISPATCHER.addKeyword(
        "Dict",
        (p, o) -> {
          final AtomicReference<ExpressionNode> key = new AtomicReference<>();
          final AtomicReference<FetchNode> value = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, key::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(FetchNode::parse, value::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new FetchCollectNodeDictionary(key.get(), value.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser parser, Consumer<FetchCollectNode> output) {
    return parser.whitespace().dispatch(DISPATCHER, output).whitespace();
  }

  public abstract Imyhat comparatorType();

  public abstract String operation();

  public abstract String renderEcma(EcmaScriptRenderer renderer);

  public abstract boolean resolve(NameDefinitions collectorName, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract Imyhat type();

  public abstract boolean typeCheck(Imyhat imyhat, Consumer<String> errorHandler);
}
