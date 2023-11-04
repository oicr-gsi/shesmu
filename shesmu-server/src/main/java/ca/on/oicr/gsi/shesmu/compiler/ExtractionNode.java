package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class ExtractionNode {
  public interface OutputCollector {
    void addValue(String name, Consumer<Renderer> value, Imyhat type);
  }

  public static Parser parse(Parser input, Consumer<ExtractionNode> output) {
    final var gangInitial = input.whitespace().symbol("@").whitespace();
    if (gangInitial.isGood()) {
      return gangInitial
          .identifier(i -> output.accept(new ExtractionNodeGang(input.line(), input.column(), i)))
          .whitespace();
    }
    final var preparedInitial = input.whitespace().symbol("$").whitespace();
    if (preparedInitial.isGood()) {
      return preparedInitial
          .identifier(
              i -> output.accept(new ExtractionNodePrepared(input.line(), input.column(), i)))
          .whitespace();
    }
    final var name = new AtomicReference<String>();
    final var nameInitial = input.whitespace().identifier(name::set).whitespace();
    final var assignment = nameInitial.symbol("=").whitespace();
    if (assignment.isGood()) {
      return assignment.then(
          ExpressionNode::parse,
          value ->
              output.accept(
                  new ExtractionNodeNamed(input.line(), input.column(), name.get(), value)));
    } else {
      output.accept(new ExtractionNodeVariable(input.line(), input.column(), name.get()));
      return nameInitial;
    }
  }

  private final int column;
  private final int line;

  protected ExtractionNode(int line, int column) {
    this.line = line;
    this.column = column;
  }

  public abstract void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate);

  public int column() {
    return column;
  }

  public int line() {
    return line;
  }

  public abstract Stream<String> names();

  /** Produce bytecode for this expression */
  public abstract void render(OutputCollector outputCollector);

  /** Resolve all variable plugins in this expression and its children. */
  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  /** Resolve all function plugins in this expression */
  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public boolean resolvePrepared(
      String context,
      Map<String, List<ExtractionNode>> prepared,
      Set<String> used,
      Consumer<String> errorHandler) {
    return true;
  }

  /** Perform type checking on this expression and its children. */
  public abstract boolean typeCheck(Consumer<String> errorHandler, OutputFormat outputFormat);
}
