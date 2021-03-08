package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class FetchNode {

  private static final ParseDispatch<FetchNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword("ActionCount", actions("count", Imyhat.INTEGER));
    DISPATCH.addKeyword("ActionIdentifiers", actions("action-ids", Imyhat.STRING.asList()));
    DISPATCH.addKeyword("ActionTags", actions("action-tags", Imyhat.STRING.asList()));
    DISPATCH.addKeyword(
        "Olive",
        (p, o) -> {
          final var format = new AtomicReference<String>();
          final var clauses = new AtomicReference<List<OliveClauseNode>>();
          final var start =
              p.whitespace().keyword("Input").whitespace().identifier(format::set).whitespace();
          final var clausesResult = start.list(clauses::set, OliveClauseNode::parse);
          final var result = clausesResult.whitespace();
          if (result.isGood()) {
            o.accept(
                new FetchNodeOlive(
                    p.line(), p.column(), format.get(), clauses.get(), start.slice(clausesResult)));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "For",
        (p, o) -> {
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var source = new AtomicReference<SourceNode>();
          final var transforms = new AtomicReference<List<ListNode>>();
          final var collector = new AtomicReference<FetchCollectNode>();
          final var result =
              p.whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .then(SourceNode::parse, source::set)
                  .whitespace()
                  .symbol(":")
                  .whitespace()
                  .list(transforms::set, ListNode::parse)
                  .whitespace()
                  .then(FetchCollectNode::parse, collector::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new FetchNodeFor(name.get(), source.get(), transforms.get(), collector.get()));
          }
          return result;
        });
    DISPATCH.addRaw(
        "Constant or Function",
        (p, o) -> {
          final var constantName = new AtomicReference<String>();
          final var results = p.qualifiedIdentifier(constantName::set).whitespace();
          if (results.lookAhead('(')) {
            // assume this is a function call
            final var arguments = new AtomicReference<List<ExpressionNode>>();
            final var funcResults =
                results
                    .symbol("(")
                    .list(arguments::set, ExpressionNode::parse, ',')
                    .symbol(")")
                    .whitespace();
            if (funcResults.isGood()) {
              o.accept(
                  new FetchNodeFunction(p.line(), p.column(), constantName.get(), arguments.get()));
            }
            return funcResults;
          }
          if (results.isGood()) {
            o.accept(new FetchNodeConstant(p.line(), p.column(), constantName.get()));
          }
          return results;
        });
  }

  private static Rule<FetchNode> actions(String fetchType, Imyhat type) {
    return (parser, output) -> {
      final var filter =
          new AtomicReference<
              ActionFilter.ActionFilterNode<
                  InformationParameterNode<ActionState>,
                  InformationParameterNode<String>,
                  InformationParameterNode<Instant>,
                  InformationParameterNode<Long>>>();
      final var result =
          ActionFilter.parse(
                  parser.whitespace(),
                  InformationParameterNode.ACTION_STATE,
                  InformationParameterNode.STRING,
                  InformationParameterNode.STRINGS,
                  InformationParameterNode.INSTANT,
                  InformationParameterNode.OFFSET,
                  filter::set)
              .whitespace();
      if (result.isGood()) {
        output.accept(new FetchNodeActions(fetchType, type, filter.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser parser, Consumer<FetchNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  public static Parser parseAsDefinition(Parser parser, Consumer<Pair<String, FetchNode>> output) {
    final var name = new AtomicReference<String>();
    return parser
        .whitespace()
        .identifier(name::set)
        .whitespace()
        .symbol("=")
        .whitespace()
        .dispatch(DISPATCH, fetch -> output.accept(new Pair<>(name.get(), fetch)))
        .whitespace();
  }

  protected FetchNode() {}

  public abstract String renderEcma(EcmaScriptRenderer r);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract Imyhat type();

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
