package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Base type for an olive clause */
public abstract class OliveClauseNode {
  private static final Pattern HELP = Pattern.compile("^\"([^\"]*)\"");
  private static final Pattern OPTIMA = Pattern.compile("^(Min|Max)");

  public static Parser parse(Parser input, Consumer<OliveClauseNode> output) {
    input = input.whitespace();
    Parser inner = parseMonitor(input, output);
    if (inner != null) {
      return inner;
    }
    inner = parseDump(input, output);
    if (inner != null) {
      return inner;
    }
    final Parser whereParser = input.keyword("Where");
    if (whereParser.isGood()) {
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final Parser result =
          ExpressionNode.parse(whereParser.whitespace(), expression::set).whitespace();
      if (result.isGood()) {
        output.accept(new OliveClauseNodeWhere(input.line(), input.column(), expression.get()));
      }
      return result;
    }
    final Parser groupParser = input.keyword("Group");
    if (groupParser.isGood()) {
      final AtomicReference<List<GroupNode>> collectors =
          new AtomicReference<>(Collections.emptyList());
      final AtomicReference<List<DiscriminatorNode>> discriminators = new AtomicReference<>();
      final AtomicReference<String> name = new AtomicReference<>();
      final AtomicReference<List<Pair<String, ExpressionNode>>> inputs = new AtomicReference<>();
      final AtomicReference<List<String>> outputs = new AtomicReference<>();

      Parser result = groupParser.whitespace().keyword("By");
      if (result.isGood()) {
        result =
            result
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
                    input.line(), input.column(), collectors.get(), discriminators.get()));
          } else {
            output.accept(
                new OliveClauseNodeGroupWithGrouper(
                    input.line(),
                    input.column(),
                    name.get(),
                    inputs.get(),
                    outputs.get(),
                    collectors.get(),
                    discriminators.get()));
          }
        }
      } else {
        // This is the old syntax for grouping.
        result =
            groupParser
                .whitespace()
                .listEmpty(collectors::set, GroupNode::parse, ',')
                .whitespace()
                .keyword("By")
                .whitespace()
                .list(discriminators::set, DiscriminatorNode::parse, ',')
                .whitespace();
        if (result.isGood()) {
          output.accept(
              new OliveClauseNodeGroup(
                  input.line(), input.column(), collectors.get(), discriminators.get()));
        }
      }
      return result;
    }
    final Parser leftJoinParser = input.keyword("LeftJoin");
    if (leftJoinParser.isGood()) {
      final AtomicReference<ExpressionNode> outerKey = new AtomicReference<>();
      final AtomicReference<String> format = new AtomicReference<>();
      final AtomicReference<ExpressionNode> innerKey = new AtomicReference<>();
      final AtomicReference<List<GroupNode>> groups = new AtomicReference<>();
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
              .list(groups::set, GroupNode::parse, ',')
              .whitespace();
      if (result.isGood()) {
        output.accept(
            new OliveClauseNodeLeftJoin(
                input.line(),
                input.column(),
                format.get(),
                outerKey.get(),
                innerKey.get(),
                groups.get()));
      }
      return result;
    }
    final Parser letParser = input.keyword("Let");
    if (letParser.isGood()) {
      final AtomicReference<List<LetArgumentNode>> arguments = new AtomicReference<>();
      final Parser result =
          letParser
              .whitespace()
              .listEmpty(arguments::set, LetArgumentNode::parse, ',')
              .whitespace();

      if (result.isGood()) {
        output.accept(new OliveClauseNodeLet(input.line(), input.column(), arguments.get()));
      }
      return result;
    }
    final Parser rejectParser = input.keyword("Reject");
    if (rejectParser.isGood()) {
      final AtomicReference<List<RejectNode>> handlers = new AtomicReference<>();
      final AtomicReference<ExpressionNode> clause = new AtomicReference<>();
      final Parser result =
          rejectParser
              .whitespace()
              .then(ExpressionNode::parse, clause::set)
              .whitespace()
              .symbol("{")
              .whitespace()
              .listEmpty(handlers::set, OliveClauseNode::parseReject, ',')
              .whitespace()
              .symbol("}")
              .whitespace();

      if (result.isGood()) {
        output.accept(
            new OliveClauseNodeReject(input.line(), input.column(), clause.get(), handlers.get()));
      }
      return result;
    }
    final Parser joinParser = input.keyword("Join");
    if (joinParser.isGood()) {
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
                input.line(), input.column(), format.get(), outerKey.get(), innerKey.get()));
      }
      return result;
    }
    final Parser pickParser = input.keyword("Pick");
    if (pickParser.isGood()) {
      final AtomicReference<Boolean> direction = new AtomicReference<>();
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final AtomicReference<List<String>> discriminators = new AtomicReference<>();
      final Parser result =
          pickParser
              .whitespace()
              .regex(OPTIMA, m -> direction.set(m.group().equals("Max")), "Need Min or Max.")
              .whitespace()
              .then(ExpressionNode::parse, expression::set)
              .whitespace()
              .keyword("By")
              .whitespace()
              .list(discriminators::set, (p, o) -> p.whitespace().identifier(o).whitespace(), ',')
              .whitespace();
      if (result.isGood()) {
        output.accept(
            new OliveClauseNodePick(
                input.line(),
                input.column(),
                direction.get(),
                expression.get(),
                discriminators.get()));
      }
      return result;
    }
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
            new OliveClauseNodeCall(input.line(), input.column(), name.get(), arguments.get()));
      }
      return result;
    }
    return input.raise("Expected olive clause.");
  }

  private static Parser parseDump(Parser input, Consumer<? super OliveClauseNodeDump> output) {
    final Parser dumpParser = input.keyword("Dump");
    if (dumpParser.isGood()) {
      final AtomicReference<List<ExpressionNode>> columns = new AtomicReference<>();
      final AtomicReference<String> dumper = new AtomicReference<>();
      final Parser result =
          dumpParser
              .whitespace()
              .listEmpty(columns::set, ExpressionNode::parse, ',')
              .whitespace()
              .keyword("To")
              .whitespace()
              .identifier(dumper::set)
              .whitespace();

      if (result.isGood()) {
        output.accept(
            new OliveClauseNodeDump(input.line(), input.column(), dumper.get(), columns.get()));
      }
      return result;
    }
    return null;
  }

  private static Parser parseMonitor(
      Parser input, Consumer<? super OliveClauseNodeMonitor> output) {
    final Parser monitorParser = input.keyword("Monitor");
    if (monitorParser.isGood()) {
      final AtomicReference<String> metricName = new AtomicReference<>();
      final AtomicReference<String> help = new AtomicReference<>();
      final AtomicReference<List<MonitorArgumentNode>> labels = new AtomicReference<>();

      final Parser result =
          monitorParser
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
    return null;
  }

  public static Parser parseReject(Parser input, Consumer<RejectNode> output) {
    Parser inner = parseMonitor(input, output);
    if (inner != null) {
      return inner;
    }
    inner = parseDump(input, output);
    if (inner != null) {
      return inner;
    }
    return input.raise("Expected olive clause.");
  }

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
   * @param inputFormatDefinition the input format for this olive
   * @param definedFormats the function to find input formats by name
   * @param defs the variable plugins available to this clause
   * @param signatureDefinitions
   * @return the variable plugins available to the next clause
   */
  public abstract NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler);

  /** Resolve all non-variable plugins */
  public abstract boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler);

  /** Type any expression in the clause */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
