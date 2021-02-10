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

  private static final ParseDispatch<FetchNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword("ActionCount", actions("count", Imyhat.INTEGER));
    DISPATCH.addKeyword("ActionIdentifiers", actions("action-ids", Imyhat.STRING.asList()));
    DISPATCH.addKeyword("ActionTags", actions("action-tags", Imyhat.STRING.asList()));
    DISPATCH.addKeyword(
        "Olive",
        (p, o) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<String> format = new AtomicReference<>();
          final AtomicReference<List<OliveClauseNode>> clauses = new AtomicReference<>();
          final Parser start =
              p.whitespace().keyword("Input").whitespace().identifier(format::set).whitespace();
          final Parser clausesResult = start.list(clauses::set, OliveClauseNode::parse);
          final Parser result =
              clausesResult
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .identifier(name::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new FetchNodeOlive(
                    p.line(),
                    p.column(),
                    name.get(),
                    format.get(),
                    clauses.get(),
                    start.slice(clausesResult)));
          }
          return result;
        });
  }

  private static Rule<FetchNode> actions(String fetchType, Imyhat type) {
    return (parser, output) -> {
      final AtomicReference<String> name = new AtomicReference<>();
      final AtomicReference<
              ActionFilter.ActionFilterNode<
                  InformationParameterNode<ActionState>,
                  InformationParameterNode<String>,
                  InformationParameterNode<Instant>,
                  InformationParameterNode<Long>>>
          filter = new AtomicReference<>();
      final Parser result =
          ActionFilter.parse(
                  parser.whitespace(),
                  InformationParameterNode.ACTION_STATE,
                  InformationParameterNode.STRINGS,
                  InformationParameterNode.INSTANT,
                  InformationParameterNode.OFFSET,
                  filter::set)
              .whitespace()
              .keyword("To")
              .whitespace()
              .identifier(name::set)
              .whitespace();
      if (result.isGood()) {
        output.accept(new FetchNodeActions(fetchType, type, name.get(), filter.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser parser, Consumer<FetchNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
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
