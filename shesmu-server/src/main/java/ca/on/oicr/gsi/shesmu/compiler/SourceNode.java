package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The provider of a stream of items for a <tt>For</tt> expression */
public abstract class SourceNode {

  private static final Parser.ParseDispatch<SourceNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "In",
        (p, o) -> {
          final AtomicReference<ExpressionNode> source = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse, source::set).whitespace();
          if (result.isGood()) {
            o.accept(new SourceNodeContainer(p.line(), p.column(), source.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Fields",
        (p, o) -> {
          final AtomicReference<ExpressionNode> source = new AtomicReference<>();
          final Parser result =
              p.whitespace().then(ExpressionNode::parse, source::set).whitespace();
          if (result.isGood()) {
            o.accept(new SourceNodeJsonObject(p.line(), p.column(), source.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Splitting",
        (p, o) -> {
          final AtomicReference<ExpressionNode> source = new AtomicReference<>();
          final AtomicReference<Pair<String, Integer>> regex = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, source::set)
                  .whitespace()
                  .keyword("By")
                  .whitespace()
                  .regex(
                      ExpressionNode.REGEX,
                      ExpressionNode.regexParser(regex),
                      "Regular expression.")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new SourceNodeSplit(
                    p.line(), p.column(), regex.get().first(), regex.get().second(), source.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Zipping",
        (p, o) -> {
          final AtomicReference<ExpressionNode> left = new AtomicReference<>();
          final AtomicReference<ExpressionNode> right = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, left::set)
                  .whitespace()
                  .keyword("With")
                  .whitespace()
                  .then(ExpressionNode::parse, right::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new SourceNodeZipper(p.line(), p.column(), left.get(), right.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "From",
        (p, o) -> {
          final AtomicReference<ExpressionNode> start = new AtomicReference<>();
          final AtomicReference<ExpressionNode> end = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .then(ExpressionNode::parse, start::set)
                  .whitespace()
                  .keyword("To")
                  .whitespace()
                  .then(ExpressionNode::parse, end::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(new SourceNodeRange(p.line(), p.column(), start.get(), end.get()));
          }
          return result;
        });
  }

  public static Parser parse(Parser input, Consumer<SourceNode> output) {
    return input.dispatch(DISPATCH, output);
  }

  private final int column;

  private final int line;

  public SourceNode(int line, int column) {
    super();
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

  public abstract Ordering ordering();

  /** Produce bytecode for this source */
  public abstract JavaStreamBuilder render(Renderer renderer);

  /** Resolve all variable plugins in this source and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all function plugins in this source */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  /**
   * The type of the items in the resulting stream
   *
   * <p>This should return {@link Imyhat#BAD} if no type can be determined
   */
  public abstract Imyhat streamType();

  /** Perform type checking on this source and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
