package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ExpressionNodeBinary extends ExpressionNode {

  private final ExpressionNode left;
  private BinaryOperation operation = BinaryOperation.BAD;
  private final Supplier<Stream<BinaryOperation.Definition>> operations;
  private final ExpressionNode right;
  private final String symbol;

  public ExpressionNodeBinary(
      Supplier<Stream<BinaryOperation.Definition>> operations,
      String symbol,
      int line,
      int column,
      ExpressionNode left,
      ExpressionNode right) {
    super(line, column);
    this.operations = operations;
    this.symbol = symbol;
    this.left = left;
    this.right = right;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    left.collectFreeVariables(names, predicate);
    right.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    left.collectPlugins(pluginFileNames);
    right.collectPlugins(pluginFileNames);
  }

  public ExpressionNode left() {
    return left;
  }

  @Override
  public void render(Renderer renderer) {
    operation.render(line(), column(), renderer, left, right);
  }

  @Override
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return left.resolveDefinitions(expressionCompilerServices, errorHandler)
        & right.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  public ExpressionNode right() {
    return right;
  }

  @Override
  public final Imyhat type() {
    return operation.returnType();
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    final boolean ok = left.typeCheck(errorHandler) & right.typeCheck(errorHandler);
    if (ok) {
      final Optional<BinaryOperation> operation =
          operations
              .get()
              .flatMap(
                  def ->
                      def.match(left.type(), right.type()).map(Stream::of).orElseGet(Stream::empty))
              .findFirst();
      if (operation.isPresent()) {
        this.operation = operation.get();
        return true;
      }
      errorHandler.accept(
          String.format(
              "%d:%d: No operation %s %s %s is defined.",
              line(), column(), left.type().name(), symbol, right.type().name()));
    }
    return false;
  }
}
