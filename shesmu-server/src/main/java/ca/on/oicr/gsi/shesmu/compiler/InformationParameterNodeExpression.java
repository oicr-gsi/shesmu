package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import java.util.function.Consumer;

public class InformationParameterNodeExpression<T> extends InformationParameterNode<T> {

  private final boolean canFlatten;
  private final Imyhat imyhat;
  private final ExpressionNode node;
  private final String suffix;

  public InformationParameterNodeExpression(
      ExpressionNode node, TypeGuarantee<T> guarantee, boolean canFlatten, String suffix) {
    super();
    this.node = node;
    this.imyhat = guarantee.type();
    this.canFlatten = canFlatten;
    this.suffix = suffix;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return (canFlatten && imyhat.isAssignableFrom(node.type())
            ? String.format("[%s]", node.renderEcma(renderer))
            : node.renderEcma(renderer))
        + suffix;
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return node.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return node.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (node.typeCheck(errorHandler)) {
      if (imyhat.isAssignableFrom(node.type())
          || canFlatten && imyhat.asList().isAssignableFrom(node.type())) {
        return true;
      }
      node.typeError(
          canFlatten ? imyhat.name() + " or " + imyhat.asList().name() : imyhat.name(),
          node.type(),
          errorHandler);
    }
    return false;
  }
}
