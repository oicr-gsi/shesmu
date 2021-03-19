package ca.on.oicr.gsi.shesmu.compiler;

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

public abstract class FetchNode implements Target {
  private interface FetchNodeConstructor {
    FetchNode create(String name);
  }

  private static final ParseDispatch<FetchNodeConstructor> DISPATCH = new ParseDispatch<>();

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
                name ->
                    new FetchNodeOlive(
                        p.line(),
                        p.column(),
                        name,
                        format.get(),
                        clauses.get(),
                        start.slice(clausesResult)));
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
                  name ->
                      new FetchNodeFunction(
                          name, p.line(), p.column(), constantName.get(), arguments.get()));
            }
            return funcResults;
          }
          if (results.isGood()) {
            o.accept(name -> new FetchNodeConstant(p.line(), p.column(), name, constantName.get()));
          }

          return results;
        });
  }

  private static Rule<FetchNodeConstructor> actions(String fetchType, Imyhat type) {
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
        output.accept(name -> new FetchNodeActions(fetchType, type, name, filter.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser parser, Consumer<FetchNode> output) {
    final var name = new AtomicReference<String>();
    return parser
        .whitespace()
        .identifier(name::set)
        .whitespace()
        .symbol("=")
        .whitespace()
        .dispatch(DISPATCH, ctor -> output.accept(ctor.create(name.get())))
        .whitespace();
  }

  private final String name;

  protected FetchNode(String name) {
    this.name = name;
  }

  public final String name() {
    return name;
  }

  public abstract String renderEcma(EcmaScriptRenderer r);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
