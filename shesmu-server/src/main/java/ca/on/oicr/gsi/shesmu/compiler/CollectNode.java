package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.CollectNodeConcatenate.ConcatenationType;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The terminal operations in <code>For</code> expressions */
public abstract class CollectNode {
  private interface DefaultConstructor {
    CollectNode create(int line, int column, ExpressionNode selector, ExpressionNode alternative);
  }

  private interface OptionalConstructor {
    CollectNode create(int line, int column, ExpressionNode selector);
  }

  private static final Parser.ParseDispatch<CollectNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Dict",
        (p, o) -> {
          final var key = new AtomicReference<ExpressionNode>();
          final var value = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse0, key::set)
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse0, value::set);
          if (result.isGood()) {
            o.accept(new CollectNodeDictionary(p.line(), p.column(), key.get(), value.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "List",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodeList(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Tuple",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var size = new AtomicLong();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse0, expression::set)
                  .whitespace()
                  .keyword("Require")
                  .whitespace()
                  .integer(size::set, 10)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new CollectNodeTuple(p.line(), p.column(), size.intValue(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "PartitionCount",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodePartitionCount(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Sum",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodeSum(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Stats",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var result = p.whitespace().then(ExpressionNode::parse0, expression::set);
          if (result.isGood()) {
            o.accept(new CollectNodeStats(p.line(), p.column(), expression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Count",
        (p, o) -> {
          o.accept(new CollectNodeCount(p.line(), p.column()));
          return p;
        });
    DISPATCH.addKeyword("First", optional(CollectNodeFirst::new));
    DISPATCH.addKeyword("Univalued", optional(CollectNodeUnivalued::new));
    DISPATCH.addKeyword("Max", optima(true));
    DISPATCH.addKeyword("Min", optima(false));
    DISPATCH.addKeyword(
        "Reduce",
        (p, o) -> {
          final var accumulatorName = new AtomicReference<DestructuredArgumentNode>();
          final var defaultExpression = new AtomicReference<ExpressionNode>();
          final var initialExpression = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, accumulatorName::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse, initialExpression::set)
                  .symbol(")")
                  .whitespace()
                  .then(ExpressionNode::parse0, defaultExpression::set);
          if (result.isGood()) {
            o.accept(
                new CollectNodeReduce(
                    p.line(),
                    p.column(),
                    accumulatorName.get(),
                    defaultExpression.get(),
                    initialExpression.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Table",
        (p, o) -> {
          final var format = new AtomicReference<ExpressionNode>();
          final var columns = new AtomicReference<List<Pair<ExpressionNode, ExpressionNode>>>();
          final var result =
              p.list(
                      columns::set,
                      (cp, co) -> {
                        final var header = new AtomicReference<ExpressionNode>();
                        final var data = new AtomicReference<ExpressionNode>();
                        final var columnResult =
                            cp.whitespace()
                                .then(ExpressionNode::parse, header::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .then(ExpressionNode::parse, data::set)
                                .whitespace();
                        if (columnResult.isGood()) {
                          co.accept(new Pair<>(header.get(), data.get()));
                        }
                        return columnResult;
                      },
                      ',')
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .then(ExpressionNode::parse, format::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new CollectNodeTable(p.line(), p.column(), format.get(), columns.get()));
          }
          return result;
        });
    for (final var matchType : Match.values()) {
      DISPATCH.addKeyword(
          matchType.syntax(),
          (p, o) -> {
            final var selectExpression = new AtomicReference<ExpressionNode>();
            final var result =
                p.whitespace().then(ExpressionNode::parse0, selectExpression::set).whitespace();
            if (result.isGood()) {
              o.accept(
                  new CollectNodeMatches(p.line(), p.column(), matchType, selectExpression.get()));
            }
            return result;
          });
    }
    for (final var concatType : ConcatenationType.values()) {
      DISPATCH.addKeyword(
          concatType.syntax(),
          (p, o) -> {
            final var getterExpression = new AtomicReference<ExpressionNode>();
            final var delimiterExpression = new AtomicReference<ExpressionNode>();
            final var result =
                p.whitespace()
                    .then(ExpressionNode::parse, getterExpression::set)
                    .keyword("With")
                    .whitespace()
                    .then(ExpressionNode::parse0, delimiterExpression::set);
            if (result.isGood()) {
              o.accept(
                  new CollectNodeConcatenate(
                      p.line(),
                      p.column(),
                      concatType,
                      getterExpression.get(),
                      delimiterExpression.get()));
            }
            return result;
          });
    }
    DISPATCH.addSymbol(
        "{",
        (p, o) -> {
          final var fields = new AtomicReference<List<CollectFieldNode>>();
          final var result =
              p.list(fields::set, CollectFieldNode::parse, ',').symbol("}").whitespace();
          if (result.isGood()) {
            o.accept(new CollectNodeObject(p.line(), p.column(), fields.get()));
          }
          return result;
        });
  }

  private static Rule<CollectNode> optima(boolean max) {
    return optional((l, c, s) -> new CollectNodeOptima(l, c, max, s));
  }

  public static Parser parse(Parser parser, Consumer<CollectNode> output) {
    return parser.dispatch(DISPATCH, output);
  }

  private static Rule<CollectNode> optional(OptionalConstructor optionalConstructor) {
    return (p, o) -> {
      final var selectExpression = new AtomicReference<ExpressionNode>();
      var result = p.whitespace().then(ExpressionNode::parse0, selectExpression::set);
      if (result.isGood()) {
        o.accept(optionalConstructor.create(p.line(), p.column(), selectExpression.get()));
      }
      return result;
    };
  }

  private final int column;

  private final int line;

  protected CollectNode(int line, int column) {
    this.line = line;
    this.column = column;
  }

  /** Add all free variable names to the set provided. */
  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public int column() {
    return column;
  }

  public int line() {
    return line;
  }

  public boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
    return true;
  }

  public abstract void render(JavaStreamBuilder builder, LoadableConstructor name);

  public abstract String render(EcmaStreamBuilder builder, EcmaLoadableConstructor name);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(
      DestructuredArgumentNode name, NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all functions plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Imyhat type();

  public abstract boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler);
}
