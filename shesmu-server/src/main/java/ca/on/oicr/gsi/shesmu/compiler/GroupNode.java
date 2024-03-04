package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.Rule;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A collection action in a “Group” clause
 *
 * <p>Also usable as the variable definition for the result
 */
public abstract class GroupNode implements DefinedTarget {
  private interface ParseGroup {
    GroupNode make(int line, int column, String name);
  }

  private interface ParseGroupWithExpression {
    GroupNode make(int line, int column, String name, ExpressionNode expression);
  }

  private interface ParseGroupWithExpressionDefaultable {
    GroupNodeDefaultable make(int line, int column, String name, ExpressionNode expression);
  }

  private static final Parser.ParseDispatch<ParseGroup> GROUPERS = new Parser.ParseDispatch<>();

  static {
    GROUPERS.addKeyword(
        "Count",
        (p, o) -> {
          o.accept(GroupNodeCount::new);
          return p;
        });
    GROUPERS.addKeyword("First", ofWithDefault(GroupNodeFirst::new));
    GROUPERS.addKeyword("Flatten", of(GroupNodeFlatten::new));
    GROUPERS.addKeyword("OnlyIf", of(GroupNodeOnlyIf::new));
    GROUPERS.addKeyword("List", of(GroupNodeList::new));
    GROUPERS.addKeyword(
        "LexicalConcat",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var delimiter = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse0, expression::set)
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .then(ExpressionNode::parse0, delimiter::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                (line, column, name) ->
                    new GroupNodeLexicalConcat(
                        line, column, name, expression.get(), delimiter.get()));
          }
          return result;
        });
    GROUPERS.addKeyword("PartitionCount", of(GroupNodePartitionCount::new));
    GROUPERS.addKeyword("Sum", of(GroupNodeSum::new));
    GROUPERS.addKeyword("Univalued", ofWithDefault(GroupNodeUnivalued::new));
    GROUPERS.addKeyword(
        "Max",
        ofWithDefault(
            (line, column, name, expression) ->
                new GroupNodeOptima(line, column, name, expression, true)));
    GROUPERS.addKeyword(
        "Min",
        ofWithDefault(
            (line, column, name, expression) ->
                new GroupNodeOptima(line, column, name, expression, false)));
    for (final var match : Match.values()) {
      GROUPERS.addKeyword(
          match.syntax(),
          of(
              (line, column, name, expression) ->
                  new GroupNodeMatches(line, column, name, match, expression)));
    }
    GROUPERS.addKeyword(
        "Dict",
        (p, o) -> {
          final var key = new AtomicReference<ExpressionNode>();
          final var value = new AtomicReference<ExpressionNode>();
          final var result =
              p.whitespace()
                  .then(ExpressionNode::parse, key::set)
                  .whitespace()
                  .symbol("=")
                  .whitespace()
                  .then(ExpressionNode::parse, value::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                (line, column, name) ->
                    new GroupNodeDictionary(line, column, name, key.get(), value.get()));
          }
          return result;
        });
    GROUPERS.addKeyword(
        "Where",
        (p, o) -> {
          final var expression = new AtomicReference<ExpressionNode>();
          final var sink = new AtomicReference<ParseGroup>();
          final var intermediate =
              p.whitespace().then(ExpressionNode::parse, expression::set).whitespace();
          final var result = intermediate.dispatch(GROUPERS, sink::set).whitespace();
          if (result.isGood()) {
            o.accept(
                (line, column, name) ->
                    new GroupNodeWhere(
                        line,
                        column,
                        expression.get(),
                        sink.get().make(intermediate.line(), intermediate.column(), name)));
          }
          return result;
        });
    GROUPERS.addSymbol(
        "{",
        (p, o) -> {
          final var children = new AtomicReference<List<GroupNode>>();
          final var result =
              p.whitespace()
                  .list(
                      children::set,
                      (cp, co) -> {
                        final var name = new AtomicReference<String>();
                        final var child = new AtomicReference<ParseGroup>();
                        final var childResult =
                            cp.whitespace()
                                .identifier(name::set)
                                .whitespace()
                                .symbol("=")
                                .whitespace()
                                .dispatch(GROUPERS, child::set)
                                .whitespace();
                        if (childResult.isGood()) {
                          co.accept(child.get().make(cp.line(), cp.column(), name.get()));
                        }
                        return childResult;
                      },
                      ',')
                  .whitespace()
                  .symbol("}")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                (line, column, name) -> new GroupNodeObject(line, column, name, children.get()));
          }
          return result;
        });
  }

  private static Rule<ParseGroup> of(ParseGroupWithExpression maker) {
    return (p, o) -> {
      final var expression = new AtomicReference<ExpressionNode>();
      final var result = p.whitespace().then(ExpressionNode::parse0, expression::set).whitespace();
      if (result.isGood()) {
        o.accept((line, column, name) -> maker.make(line, column, name, expression.get()));
      }
      return result;
    };
  }

  private static Rule<ParseGroup> ofWithDefault(ParseGroupWithExpressionDefaultable maker) {
    return (p, o) -> {
      final var expression = new AtomicReference<ExpressionNode>();
      final var result = p.whitespace().then(ExpressionNode::parse0, expression::set).whitespace();
      if (result.isGood()) {
        final var defaultResult = result.keyword("Default");
        if (defaultResult.isGood()) {
          final var initial = new AtomicReference<ExpressionNode>();
          final var defaultComplete =
              defaultResult.whitespace().then(ExpressionNode::parse0, initial::set).whitespace();
          if (defaultComplete.isGood()) {
            o.accept(
                (line, column, name) ->
                    new GroupNodeWithDefault(
                        line,
                        column,
                        initial.get(),
                        maker.make(line, column, name, expression.get())));
          }
          return defaultComplete;
        }
        o.accept((line, column, name) -> maker.make(line, column, name, expression.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser input, Consumer<GroupNode> output) {
    final var name = new AtomicReference<String>();

    return input
        .whitespace()
        .identifier(name::set)
        .whitespace()
        .keyword("=")
        .whitespace()
        .dispatch(
            GROUPERS, maker -> output.accept(maker.make(input.line(), input.column(), name.get())));
  }

  private final int column;
  private final int line;

  public GroupNode(int line, int column) {
    this.line = line;
    this.column = column;
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  @Override
  public final int column() {
    return column;
  }

  @Override
  public final Flavour flavour() {
    return Flavour.STREAM;
  }

  public abstract boolean isRead();

  @Override
  public final int line() {
    return line;
  }

  public abstract void render(Regrouper regroup, RootBuilder builder);

  public abstract boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
