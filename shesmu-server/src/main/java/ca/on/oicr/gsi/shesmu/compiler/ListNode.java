package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** One of the stream modification operations in a <tt>For</tt> expression */
public abstract class ListNode {
  private interface ListNodeConstructor {
    public ListNode build(int line, int column, ExpressionNode expression);
  }

  private interface ListNodeNamedConstructor {
    public ListNode build(
        int line, int column, DestructuredArgumentNode name, ExpressionNode expression);
  }

  public enum Ordering {
    BAD,
    RANDOM,
    REQESTED
  }

  private static final Parser.ParseDispatch<ListNode> DISPATCH = new Parser.ParseDispatch<>();

  static {
    DISPATCH.addKeyword("Let", handler(ListNodeMap::new, p -> p.symbol("=")));
    DISPATCH.addKeyword(
        "Flatten",
        (p, o) -> {
          final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
          final AtomicReference<SourceNode> source = new AtomicReference<>();
          final AtomicReference<List<ListNode>> transforms = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .then(DestructuredArgumentNode::parse, name::set)
                  .whitespace()
                  .then(SourceNode::parse, source::set)
                  .list(transforms::set, ListNode::parse)
                  .symbol(")")
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new ListNodeFlatten(
                    p.line(), p.column(), name.get(), source.get(), transforms.get()));
          }
          return result;
        });
    DISPATCH.addKeyword("Limit", handler(ListNodeLimit::new));
    DISPATCH.addKeyword("Skip", handler(ListNodeSkip::new));
    DISPATCH.addKeyword("Where", handler(ListNodeFilter::new));
    DISPATCH.addKeyword("Sort", handler(ListNodeSort::new));
    DISPATCH.addKeyword(
        "Reverse",
        (p, o) -> {
          o.accept(new ListNodeReverse(p.line(), p.column()));
          return p.whitespace();
        });
    DISPATCH.addKeyword(
        "Distinct",
        (p, o) -> {
          o.accept(new ListNodeDistinct(p.line(), p.column()));
          return p.whitespace();
        });
    DISPATCH.addKeyword(
        "Subsample",
        (p, o) -> {
          final AtomicReference<List<SampleNode>> samplers = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .list(samplers::set, SampleNode::parse, ',')
                  .whitespace()
                  .symbol(")")
                  .whitespace();
          if (result.isGood()) {
            o.accept(new ListNodeSubsample(p.line(), p.column(), samplers.get()));
          }
          return result;
        });
  }

  private static Parser.Rule<ListNode> handler(ListNodeConstructor constructor) {
    return (p, o) -> {
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final Parser result = p.whitespace().then(ExpressionNode::parse, expression::set);
      if (result.isGood()) {
        o.accept(constructor.build(p.line(), p.column(), expression.get()));
      }
      return result;
    };
  }

  private static Parser.Rule<ListNode> handler(
      ListNodeNamedConstructor constructor, Function<Parser, Parser> linker) {
    return (p, o) -> {
      final AtomicReference<DestructuredArgumentNode> name = new AtomicReference<>();
      final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
      final Parser result =
          linker
              .apply(p.whitespace().then(DestructuredArgumentNode::parse, name::set).whitespace())
              .whitespace()
              .then(ExpressionNode::parse, expression::set);
      if (result.isGood()) {
        o.accept(constructor.build(p.line(), p.column(), name.get(), expression.get()));
      }
      return result;
    };
  }

  public static Parser parse(Parser parser, Consumer<ListNode> output) {
    return parser.whitespace().dispatch(DISPATCH, output).whitespace();
  }

  private final int column;

  private final int line;

  protected ListNode(int line, int column) {
    this.line = line;
    this.column = column;
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public abstract void collectPlugins(Set<Path> pluginFileNames);

  public int column() {
    return column;
  }

  public int line() {
    return line;
  }

  public abstract Ordering order(Ordering previous, Consumer<String> errorHandler);

  public abstract LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract Optional<List<Target>> resolve(
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all functions plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler);
}
