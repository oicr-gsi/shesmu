package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * For each question mark we encounter, we need to track the question marks inside of it. To do
 * this, we divide the operations into layers of nesting. Each layer is defined by an integer number
 * that starts at zero and decreases for every inner layer. Processing is done in layer order.
 *
 * <p>Given <code>foo(x?)? + y?</code>, this will be divided into two layers (plus the base
 * expression):
 *
 * <ul>
 *   <li>Base expression: <code>$0 + $1</code>
 *   <li>Layer 0: <code>$0 = foo($2)</code> and <code>$1 = y</code>
 *   <li>Layer -1: <code>$2 = x</code>
 * </ul>
 *
 * The order of the expressions in each layer is arbitrary, but there is no way for them to
 * interfere, so it doesn't matter.
 */
class OptionalCaptureCompilerServices implements ExpressionCompilerServices {

  private final Consumer<String> errorHandler;
  private final ExpressionCompilerServices expressionCompilerServices;
  private final int layer;
  private final Map<Integer, List<UnboxableExpression>> captures;

  public OptionalCaptureCompilerServices(
      ExpressionCompilerServices expressionCompilerServices,
      Consumer<String> errorHandler,
      Map<Integer, List<UnboxableExpression>> captures) {
    this(expressionCompilerServices, errorHandler, 0, captures);
  }

  private OptionalCaptureCompilerServices(
      ExpressionCompilerServices expressionCompilerServices,
      Consumer<String> errorHandler,
      int layer,
      Map<Integer, List<UnboxableExpression>> captures) {
    this.expressionCompilerServices = expressionCompilerServices;
    this.errorHandler = errorHandler;
    this.layer = layer;
    this.captures = captures;
  }

  @Override
  public ActionDefinition action(String name) {
    return expressionCompilerServices.action(name);
  }

  @Override
  public Optional<TargetWithContext> captureOptional(
      ExpressionNode expression, int line, int column, Consumer<String> errorHandler) {
    if (expression.resolveDefinitions(
        new OptionalCaptureCompilerServices(
            expressionCompilerServices, this.errorHandler, layer - 1, captures),
        this.errorHandler)) {
      final var target = new UnboxableExpression(expression);
      captures.computeIfAbsent(layer, k -> new ArrayList<>()).add(target);
      return Optional.of(target);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public FunctionDefinition function(String name) {
    return expressionCompilerServices.function(name);
  }

  @Override
  public InputFormatDefinition inputFormat(String format) {
    return expressionCompilerServices.inputFormat(format);
  }

  @Override
  public Imyhat imyhat(String name) {
    return expressionCompilerServices.imyhat(name);
  }

  @Override
  public InputFormatDefinition inputFormat() {
    return expressionCompilerServices.inputFormat();
  }
}
