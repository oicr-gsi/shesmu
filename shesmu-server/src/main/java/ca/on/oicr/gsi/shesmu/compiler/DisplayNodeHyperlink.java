package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;

public class DisplayNodeHyperlink extends DisplayNode {
  private final ExpressionNode label;
  private final ExpressionNode link;

  public DisplayNodeHyperlink(ExpressionNode label, ExpressionNode link) {
    this.label = label;
    this.link = link;
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return String.format(
        "{type: \"a\", url: %s, contents: %s}",
        link.renderEcma(renderer), label.renderEcma(renderer));
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return label.resolve(defs, errorHandler) & link.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return label.resolveDefinitions(expressionCompilerServices, errorHandler)
        & link.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = true;
    if (label.typeCheck(errorHandler)) {
      if (label.type().isSame(Imyhat.STRING)
          || label.type().isSame(Imyhat.STRING.asList())
          || label.type().isSame(Imyhat.INTEGER)
          || label.type().isSame(Imyhat.FLOAT)) {
      } else {
        label.typeError("string or [string] or integer or float", label.type(), errorHandler);
        ok = false;
      }
    } else {
      ok = false;
    }

    if (link.typeCheck(errorHandler)) {
      if (label.type().isSame(Imyhat.STRING)) {
      } else {
        label.typeError(Imyhat.STRING, label.type(), errorHandler);
        ok = false;
      }
    } else {
      ok = false;
    }

    return ok;
  }
}
