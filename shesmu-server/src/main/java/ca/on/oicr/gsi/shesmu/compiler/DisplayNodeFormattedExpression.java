package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class DisplayNodeFormattedExpression extends DisplayNode {
  private final ExpressionNode expression;
  private final String formatting;

  public DisplayNodeFormattedExpression(ExpressionNode expression, String formatting) {
    this.expression = expression;
    this.formatting = formatting;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{type: \"%s\", contents: %s}", formatting, expression.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type().isSame(Imyhat.STRING)
          || expression.type().isSame(Imyhat.STRING.asList())
          || expression.type().isSame(Imyhat.INTEGER)
          || expression.type().isSame(Imyhat.FLOAT)) {
        return true;
      } else {
        expression.typeError(
            "string or [string] or integer or float", expression.type(), errorHandler);
        return false;
      }
    } else {
      return false;
    }
  }
}
