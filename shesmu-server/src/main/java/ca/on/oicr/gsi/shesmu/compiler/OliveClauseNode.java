package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Base type for an olive clause */
public abstract class OliveClauseNode {
  private interface DumpConstructor {
    OliveClauseNodeBaseDump create(Optional<String> label, int line, int column, String dumperName);
  }

  interface JoinConstructor {
    OliveClauseNode create(
        Optional<String> label,
        int line,
        int column,
        JoinSourceNode source,
        ExpressionNode outerKey,
        ExpressionNode innerKey);
  }

  private interface LeftJoinConstructor {
    OliveClauseNode create(
        Optional<String> label,
        int line,
        int column,
        JoinSourceNode source,
        ExpressionNode outerKey,
        String prefix,
        ExpressionNode innerKey,
        List<GroupNode> groups,
        Optional<ExpressionNode> where);
  }

  private interface NodeConstructor<T> {
    T create(Optional<String> label);
  }

  private static final Parser.ParseDispatch<NodeConstructor<OliveClauseNode>> CLAUSES =
      new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<Optional<ExpressionNode>> GROUP_WHERE =
      new Parser.ParseDispatch<>();
  private static final Pattern HELP = Pattern.compile("^\"([^\"]*)\"");
  private static final Pattern OPTIMA = Pattern.compile("^(Min|Max)");
  private static final Parser.ParseDispatch<String> PREFIX = new Parser.ParseDispatch<>();
  private static final Parser.ParseDispatch<RejectNode> REJECT_CLAUSES =
      new Parser.ParseDispatch<>();

  static {
    REJECT_CLAUSES.addKeyword(
        "Monitor", (p, o) -> parseMonitor(p, f -> o.accept(f.create(Optional.empty()))));
    REJECT_CLAUSES.addKeyword(
        "Dump", (p, o) -> parseDump(p, f -> o.accept(f.create(Optional.empty()))));
    REJECT_CLAUSES.addKeyword(
        "Alert",
        (p, o) ->
            OliveNode.parseAlert(
                p, v -> o.accept(v.create(p.line(), p.column(), List.of(), Set.of(), ""))));

    CLAUSES.addKeyword("Monitor", (p, o) -> parseMonitor(p, f -> o.accept(f::create)));
    CLAUSES.addKeyword("Dump", (p, o) -> parseDump(p, f -> o.accept(f::create)));
    CLAUSES.addKeyword(
        "Where",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result = ExpressionNode.parse(p.whitespace(), expression::set).whitespace();
          if (result.isGood()) {
            o.accept(
                label -> new OliveClauseNodeWhere(label, p.line(), p.column(), expression.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Group",
        (parser, output) -> {
          final var collectors = new AtomicReference<List<GroupNode>>(List.of());
          final var discriminators = new AtomicReference<List<DiscriminatorNode>>();
          final var name = new AtomicReference<String>();
          final var inputs = new AtomicReference<List<Pair<String, ExpressionNode>>>();
          final var outputs = new AtomicReference<List<String>>();
          final var where = new AtomicReference<Optional<ExpressionNode>>();
          final var handlers = new AtomicReference<List<RejectNode>>(List.of());

          var result =
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
                              .qualifiedIdentifier(name::set)
                              .whitespace()
                              .list(
                                  inputs::set,
                                  (p, o) -> {
                                    final var parameterName = new AtomicReference<String>();
                                    final var expression = new AtomicReference<ExpressionNode>();
                                    final var paramResult =
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
          final var rejectResult = result.keyword("OnReject");
          if (rejectResult.isGood()) {
            result =
                rejectResult
                    .whitespace()
                    .list(handlers::set, (rp, ro) -> rp.whitespace().dispatch(REJECT_CLAUSES, ro))
                    .whitespace()
                    .keyword("Resume")
                    .whitespace();
          }

          if (result.isGood()) {
            if (name.get() == null) {
              output.accept(
                  label ->
                      new OliveClauseNodeGroup(
                          label,
                          parser.line(),
                          parser.column(),
                          collectors.get(),
                          discriminators.get(),
                          where.get(),
                          handlers.get()));
            } else {
              output.accept(
                  label ->
                      new OliveClauseNodeGroupWithGrouper(
                          label,
                          parser.line(),
                          parser.column(),
                          name.get(),
                          inputs.get(),
                          outputs.get(),
                          collectors.get(),
                          discriminators.get(),
                          where.get(),
                          handlers.get()));
            }
          }
          return result;
        });
    CLAUSES.addKeyword("LeftJoin", leftJoin(OliveClauseNodeLeftJoin::new));
    CLAUSES.addKeyword("LeftIntersectionJoin", leftJoin(OliveClauseNodeLeftIntersectionJoin::new));
    CLAUSES.addKeyword(
        "Let",
        (letParser, output) -> {
          final var arguments = new AtomicReference<List<LetArgumentNode>>();
          final var result =
              letParser
                  .whitespace()
                  .listEmpty(arguments::set, LetArgumentNode::parse, ',')
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                label ->
                    new OliveClauseNodeLet(
                        label, letParser.line(), letParser.column(), arguments.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Flatten",
        (parser, output) -> {
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
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
                label ->
                    new OliveClauseNodeFlatten(
                        label, parser.line(), parser.column(), name.get(), expression.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Reject",
        (rejectParser, output) -> {
          final var handlers = new AtomicReference<List<RejectNode>>();
          final var clause = new AtomicReference<ExpressionNode>();
          final var result =
              rejectParser
                  .whitespace()
                  .then(ExpressionNode::parse, clause::set)
                  .whitespace()
                  .keyword("OnReject")
                  .whitespace()
                  .list(handlers::set, (rp, ro) -> rp.whitespace().dispatch(REJECT_CLAUSES, ro))
                  .whitespace()
                  .keyword("Resume")
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                label ->
                    new OliveClauseNodeReject(
                        label,
                        rejectParser.line(),
                        rejectParser.column(),
                        clause.get(),
                        handlers.get()));
          }
          return result;
        });
    CLAUSES.addKeyword(
        "Require",
        (rejectParser, output) -> {
          final var handlers = new AtomicReference<List<RejectNode>>();
          final var name = new AtomicReference<DestructuredArgumentNode>();
          final var expression = new AtomicReference<ExpressionNode>();
          final var result =
              rejectParser
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse, expression::set)
                  .whitespace()
                  .keyword("OnReject")
                  .whitespace()
                  .list(handlers::set, (rp, ro) -> rp.whitespace().dispatch(REJECT_CLAUSES, ro))
                  .whitespace()
                  .keyword("Resume")
                  .whitespace();

          if (result.isGood()) {
            output.accept(
                label ->
                    new OliveClauseNodeRequire(
                        label,
                        rejectParser.line(),
                        rejectParser.column(),
                        name.get(),
                        expression.get(),
                        handlers.get()));
          }
          return result;
        });
    CLAUSES.addKeyword("Join", join(OliveClauseNodeJoin::new));
    CLAUSES.addKeyword("IntersectionJoin", join(OliveClauseNodeIntersectionJoin::new));
    CLAUSES.addKeyword(
        "Pick",
        (pickParser, output) -> {
          final var direction = new AtomicReference<Boolean>();
          final var expression = new AtomicReference<ExpressionNode>();
          final var discriminators = new AtomicReference<List<PickNode>>();
          final var result =
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
                label ->
                    new OliveClauseNodePick(
                        label,
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
          final var name = new AtomicReference<String>();
          final var callParser = input.qualifiedIdentifier(name::set);
          if (callParser.isGood()) {
            final var arguments = new AtomicReference<List<ExpressionNode>>();
            final var result =
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
                  label ->
                      new OliveClauseNodeCall(
                          label, input.line(), input.column(), name.get(), arguments.get()));
            }
            return result;
          }
          return callParser;
        });

    GROUP_WHERE.addKeyword(
        "Where",
        (p, o) -> {
          final var condition = new AtomicReference<ExpressionNode>();
          final var result =
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
    PREFIX.addKeyword("Prefix", (p, o) -> p.whitespace().identifier(o).whitespace());
    PREFIX.addRaw(
        "nothing",
        (p, o) -> {
          o.accept("");
          return p;
        });
  }

  private static Rule<NodeConstructor<OliveClauseNode>> join(JoinConstructor constructor) {
    return (joinParser, output) -> {
      final var outerKey = new AtomicReference<ExpressionNode>();
      final var source = new AtomicReference<JoinSourceNode>();
      final var innerKey = new AtomicReference<ExpressionNode>();
      final var result =
          joinParser
              .whitespace()
              .then(ExpressionNode::parse, outerKey::set)
              .whitespace()
              .keyword("To")
              .whitespace()
              .then(JoinSourceNode::parse, source::set)
              .whitespace()
              .then(ExpressionNode::parse, innerKey::set)
              .whitespace();

      if (result.isGood()) {
        output.accept(
            label ->
                constructor.create(
                    label,
                    joinParser.line(),
                    joinParser.column(),
                    source.get(),
                    outerKey.get(),
                    innerKey.get()));
      }
      return result;
    };
  }

  private static Rule<NodeConstructor<OliveClauseNode>> leftJoin(LeftJoinConstructor constructor) {
    return (leftJoinParser, output) -> {
      final var outerKey = new AtomicReference<ExpressionNode>();
      final var source = new AtomicReference<JoinSourceNode>();
      final var prefix = new AtomicReference<String>();
      final var innerKey = new AtomicReference<ExpressionNode>();
      final var groups = new AtomicReference<List<GroupNode>>();
      final var where = new AtomicReference<Optional<ExpressionNode>>();
      final var result =
          leftJoinParser
              .whitespace()
              .then(ExpressionNode::parse, outerKey::set)
              .whitespace()
              .keyword("To")
              .whitespace()
              .dispatch(PREFIX, prefix::set)
              .then(JoinSourceNode::parse, source::set)
              .whitespace()
              .then(ExpressionNode::parse, innerKey::set)
              .whitespace()
              .dispatch(GROUP_WHERE, where::set)
              .list(groups::set, GroupNode::parse, ',')
              .whitespace();
      if (result.isGood()) {
        output.accept(
            label ->
                constructor.create(
                    label,
                    leftJoinParser.line(),
                    leftJoinParser.column(),
                    source.get(),
                    outerKey.get(),
                    prefix.get(),
                    innerKey.get(),
                    groups.get(),
                    where.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser input, Consumer<OliveClauseNode> output) {
    final var labelResult = input.whitespace().keyword("Label").whitespace();
    if (labelResult.isGood()) {
      final var label = new AtomicReference<String>();
      return labelResult
          .regex(HELP, m -> label.set(m.group(1)), "label")
          .whitespace()
          .dispatch(CLAUSES, v -> output.accept(v.create(Optional.of(label.get()))));

    } else {
      return input.whitespace().dispatch(CLAUSES, v -> output.accept(v.create(Optional.empty())));
    }
  }

  private static Parser parseDump(
      Parser input, Consumer<NodeConstructor<OliveClauseNodeBaseDump>> output) {
    final DumpConstructor constructor;
    final var allResult = input.whitespace().keyword("All").whitespace();
    final Parser intermediateParser;
    if (allResult.isGood()) {
      constructor = OliveClauseNodeDumpAll::new;
      intermediateParser = allResult;
    } else {
      final var columns = new AtomicReference<List<Pair<Optional<String>, ExpressionNode>>>();
      intermediateParser =
          input
              .whitespace()
              .listEmpty(
                  columns::set,
                  (pf, po) -> {
                    final var label = new AtomicReference<Optional<String>>(Optional.empty());
                    final var labelResult = pf.whitespace().keyword("Label").whitespace();

                    return ExpressionNode.parse(
                        labelResult.isGood()
                            ? labelResult.identifier(l -> label.set(Optional.of(l))).whitespace()
                            : pf,
                        expression -> po.accept(new Pair<>(label.get(), expression)));
                  },
                  ',')
              .whitespace();
      constructor = (la, l, c, d) -> new OliveClauseNodeDump(la, l, c, d, columns.get());
    }
    final var dumper = new AtomicReference<String>();
    final var result =
        intermediateParser.keyword("To").whitespace().identifier(dumper::set).whitespace();

    if (result.isGood()) {
      output.accept(label -> constructor.create(label, input.line(), input.column(), dumper.get()));
    }
    return result;
  }

  private static Parser parseMonitor(
      Parser input, Consumer<NodeConstructor<OliveClauseNodeMonitor>> output) {
    final var metricName = new AtomicReference<String>();
    final var help = new AtomicReference<String>();
    final var labels = new AtomicReference<List<MonitorArgumentNode>>();

    final var result =
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
          label ->
              new OliveClauseNodeMonitor(
                  label, input.line(), input.column(), metricName.get(), help.get(), labels.get()));
    }
    return result;
  }

  public abstract boolean checkUnusedDeclarations(Consumer<String> errorHandler);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public abstract int column();

  public abstract Stream<OliveClauseRow> dashboard();

  /**
   * Check whether the variable stream is acceptable to the clause
   *
   * @param state the current variable state
   */
  public abstract ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler);

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
      Function<String, CallableDefinitionRenderer> definitions);

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
