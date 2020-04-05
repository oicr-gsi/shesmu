package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Base type for an olive clause */
public abstract class OliveClauseNode {
  private interface DumpConstructor {
    OliveClauseNodeBaseDump create(int line, int column, String dumperName);
  }

  public static Parser parse(Parser input, Consumer<OliveClauseNode> output) {
    return input.whitespace().dispatch(CLAUSES, output);
  }

  private static Parser parseDump(Parser input, Consumer<? super OliveClauseNodeBaseDump> output) {
    final DumpConstructor constructor;
    final Parser allResult = input.whitespace().keyword("All").whitespace();
    final Parser intermediateParser;
    if (allResult.isGood()) {
      constructor = OliveClauseNodeDumpAll::new;
      intermediateParser = allResult;
    } else {
      final AtomicReference<List<ExpressionNode>> columns = new AtomicReference<>();
      intermediateParser =
          input.whitespace().listEmpty(columns::set, ExpressionNode::parse, ',').whitespace();
      constructor = (l, c, d) -> new OliveClauseNodeDump(l, c, d, columns.get());
    }
    final AtomicReference<String> dumper = new AtomicReference<>();
    final Parser result =
        intermediateParser.keyword("To").whitespace().identifier(dumper::set).whitespace();

    if (result.isGood()) {
      output.accept(constructor.create(input.line(), input.column(), dumper.get()));
    }
    return result;
  }

  private static Parser parseMonitor(
      Parser input, Consumer<? super OliveClauseNodeMonitor> output) {
    final AtomicReference<String> metricName = new AtomicReference<>();
    final AtomicReference<String> help = new AtomicReference<>();
    final AtomicReference<List<MonitorArgumentNode>> labels = new AtomicReference<>();

    final Parser result =
        input
            .whitespace()
            .identifier(metricName::set)
            .whitespace()
            .regex(HELP, m -> help.set(m.group(1)), "Failed to parse help text")
            .whitespace()
            .symbol("{")
            .listEmpty(labels::set, MonitorArgumentNode::parse, ',')
            .symbol("}")
            .whitespace();

    if (result.isGood()) {
      output.accept(
          new OliveClauseNodeMonitor(
              input.line(), input.column(), metricName.get(), help.get(), labels.get()));
    }
    return result;
  }

  private static final Parser.ParseDispatch<OliveClauseNode> CLAUSES = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Optional<ExpressionNode>> GROUP_WHERE =
      new Parser.ParseDispatch<>();
  private static final Pattern HELP = Pattern.compile("^\"([^\"]*)\"");
  private static final Pattern OPTIMA = Pattern.compile("^(Min|Max)");
  private static final Parser.ParseDispatch<RejectNode> REJECT_CLAUSES =
      new Parser.ParseDispatch<>();

  static {
    REJECT_CLAUSES.addKeyword("Monitor", OliveClauseNode::parseMonitor);
    REJECT_CLAUSES.addKeyword("Dump", OliveClauseNode::parseDump);
    REJECT_CLAUSES.addKeyword(
        "Alert",
        (p, o) ->
            OliveNode.parseAlert(
                p,
                v ->
                    o.accept(
                        v.create(
                            p.line(),
                            p.column(),
                            Collections.emptyList(),
                            Collections.emptySet(),
                            ""))));

    CLAUSES.addKeyword("Monitor", OliveClauseNode::parseMonitor);
    CLAUSES.addKeyword("Dump", OliveClauseNode::parseDump);
    CLAUSES.addKeyword(
        "Where",
        (p, o) -> {
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result = ExpressionNode.parse(p.whitespace(), expression::set).whitespace();
          if (result.isGood()) {
            o.accept(new OliveClauseNodeWhere(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Group",
        (parser, output) -> {
          final AtomicReference<List<GroupNode>> collectors =
              new AtomicReference<>(Collections.emptyList());
          final AtomicReference<List<DiscriminatorNode>> discriminators = new AtomicReference<>();
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<List<Pair<String, ExpressionNode>>> inputs =
              new AtomicReference<>();
          final AtomicReference<List<String>> outputs = new AtomicReference<>();
          final AtomicReference<Optional<ExpressionNode>> where = new AtomicReference<>();

          Parser result =
              parser
                  .whitespace()
                  .keyword("By")
                  .whitespace()
                  .list(discriminators::set, DiscriminatorNode::parse, ',')
                  .whitespace()
                  .keyword(
                      "Using",
                      up ->
                          up.whitespace()
                              .identifier(name::set)
                              .whitespace()
                              .list(
                                  inputs::set,
                                  (p, o) -> {
                                    final AtomicReference<String> parameterName =
                                        new AtomicReference<>();
                                    final AtomicReference<ExpressionNode> expression =
                                        new AtomicReference<>();
                                    final Parser paramResult =
                                        p.whitespace()
                                            .identifier(parameterName::set)
                                            .whitespace()
                                            .symbol("=")
                                            .whitespace()
                                            .then(ExpressionNode::parse, expression::set)
                                            .whitespace();
                                    if (paramResult.isGood()) {
                                      o.accept(new Pair<>(parameterName.get(), expression.get()));
                                    }
                                    return paramResult;
                                  },
                                  ',')
                              .whitespace()
                              .keyword(
                                  "With",
                                  wp ->
                                      wp.whitespace()
                                          .listEmpty(
                                              outputs::set,
                                              (p, o) -> p.whitespace().identifier(o).whitespace(),
                                              ',')
                                          .whitespace()))
                  .dispatch(GROUP_WHERE, where::set)
                  .keyword(
                      "Into",
                      ip ->
                          ip.whitespace()
                              .listEmpty(collectors::set, GroupNode::parse, ',')
                              .whitespace());
          if (result.isGood()) {
            if (name.get() == null) {
              output.accept(
                  new OliveClauseNodeGroup(
                      parser.line(),
                      parser.column(),
                      collectors.get(),
                      discriminators.get(),
                      where.get()));
            } else {
              output.accept(
                  new OliveClauseNodeGroupWithGrouper(
                      parser.line(),
                      parser.column(),
                      name.get(),
                      inputs.get(),
                      outputs.get(),
                      collectors.get(),
                      discriminators.get(),
                      where.get()));
            }
          }
          return result;
        });
    CLAUSES.addKeyword(
        "LeftJoin",
        (leftJoinParser, output) -> {
          final AtomicReference<ExpressionNode> outerKey = new AtomicReference<>();
          final AtomicReference<String> format = new AtomicReference<>();
          final AtomicReference<ExpressionNode> innerKey = new AtomicReference<>();
          final AtomicReference<List<GroupNode>> groups = new AtomicReference<>();
          final AtomicReference<Optional<ExpressionNode>> where = new AtomicReference<>();
          final Parser result =
              leftJoinParser
                  .whitespace()
                  .then(ExpressionNode::parse, outerKey::set)
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .identifier(format::set)
                  .whitespace()
                  .then(ExpressionNode::parse, innerKey::set)
                  .whitespace()
                  .dispatch(GROUP_WHERE, where::set)
                  .list(groups::set, GroupNode::parse, ',')
                  .whitespace();
          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeLeftJoin(
                    leftJoinParser.line(),
                    leftJoinParser.column(),
                    format.get(),
                    outerKey.get(),
                    innerKey.get(),
                    groups.get(),
                    where.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Let",
        (letParser, output) -> {
          final AtomicReference<List<LetArgumentNode>> arguments = new AtomicReference<>();
          final Parser result =
              letParser
                  .whitespace()
                  .listEmpty(arguments::set, LetArgumentNode::parse, ',')
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeLet(letParser.line(), letParser.column(), arguments.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Flatten",
        (parser, output) -> {
          final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              parser
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .keyword("In")
                  .whitespace()
                  .then(ExpressionNode::parse, expression::set)
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeFlatten(
                    parser.line(), parser.column(), name.get(), expression.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Reject",
        (rejectParser, output) -> {
          final AtomicReference<List<RejectNode>> handlers = new AtomicReference<>();
          final AtomicReference<ExpressionNode> clause = new AtomicReference<>();
          final Parser result =
              rejectParser
                  .whitespace()
                  .then(ExpressionNode::parse, clause::set)
                  .whitespace()
                  .symbol("{")
                  .whitespace()
                  .listEmpty(
                      handlers::set, (rp, ro) -> rp.whitespace().dispatch(REJECT_CLAUSES, ro), ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeReject(
                    rejectParser.line(), rejectParser.column(), clause.get(), handlers.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Require",
        (rejectParser, output) -> {
          final AtomicReference<List<RejectNode>> handlers = new AtomicReference<>();
          final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final Parser result =
              rejectParser
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse, expression::set)
                  .whitespace()
                  .symbol("{")
                  .whitespace()
                  .listEmpty(
                      handlers::set, (rp, ro) -> rp.whitespace().dispatch(REJECT_CLAUSES, ro), ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeRequire(
                    rejectParser.line(),
                    rejectParser.column(),
                    name.get(),
                    expression.get(),
                    handlers.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Join",
        (joinParser, output) -> {
          final AtomicReference<ExpressionNode> outerKey = new AtomicReference<>();
          final AtomicReference<String> format = new AtomicReference<>();
          final AtomicReference<ExpressionNode> innerKey = new AtomicReference<>();
          final Parser result =
              joinParser
                  .whitespace()
                  .then(ExpressionNode::parse, outerKey::set)
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .identifier(format::set)
                  .whitespace()
                  .then(ExpressionNode::parse, innerKey::set)
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                new OliveClauseNodeJoin(
                    joinParser.line(),
                    joinParser.column(),
                    format.get(),
                    outerKey.get(),
                    innerKey.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Pick",
        (pickParser, output) -> {
          final AtomicReference<Boolean> direction = new AtomicReference<>();
          final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
          final AtomicReference<List<PickNode>> discriminators = new AtomicReference<>();
          final Parser result =
              pickParser
                  .whitespace()
                  .regex(OPTIMA, m -> direction.set(m.group().equals("Max")), "Need Min or Max.")
                  .whitespace()
                  .then(ExpressionNode::parse, expression::set)
                  .whitespace()
                  .keyword("By")
                  .whitespace()
                  .list(discriminators::set, PickNode::parse, ',')
                  .whitespace();
          if (result.isGood()) {
            output.accept(
                new OliveClauseNodePick(
                    pickParser.line(),
                    pickParser.column(),
                    direction.get(),
                    expression.get(),
                    discriminators.get()));
          }
          return result;
        });
    CLAUSES.addRaw(
        "call",
        (input, output) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final Parser callParser = input.identifier(name::set);
          if (callParser.isGood()) {
            final AtomicReference<List<ExpressionNode>> arguments = new AtomicReference<>();
            final Parser result =
                callParser
                    .whitespace()
                    .symbol("(")
                    .whitespace()
                    .listEmpty(arguments::set, ExpressionNode::parse, ',')
                    .whitespace()
                    .symbol(")")
                    .whitespace();
            if (result.isGood()) {
              output.accept(
                  new OliveClauseNodeCall(
                      input.line(), input.column(), name.get(), arguments.get()));
            }
            return result;
          }
          return callParser;
        });

    GROUP_WHERE.addKeyword(
        "Where",
        (p, o) -> {
          final AtomicReference<ExpressionNode> condition = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse, condition::set).whitespace();
          if (result.isGood()) {
            o.accept(Optional.of(condition.get()));
          }
          return result;
        });
    GROUP_WHERE.addRaw(
        "nothing",
        (p, o) -> {
          o.accept(Optional.empty());
          return p.whitespace();
        });
  }

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract int column();

  public abstract Stream<OliveClauseRow> dashboard();

  /**
   * Check whether the variable stream is acceptable to the clause
   *
   * @param state the current variable state
   */
  public abstract ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler);

  public abstract int line();

  /**
   * Generate byte code for this clause.
   *
   * <p>This will consume a stream off the stack, manipulate it as necessary, and leave a new stream
   * on the stack. Any required other classes or methods must be generated by the clause.
   */
  public abstract void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Map<String, OliveDefineBuilder> definitions);

  /**
   * Resolve all variable plugins in this clause
   *
   * @param oliveCompilerServices the input format for this olive
   * @param defs the variable plugins available to this clause
   * @return the variable plugins available to the next clause
   */
  public abstract NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler);

  /** Resolve all non-variable plugins */
  public abstract boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  /** Type any expression in the clause */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
