package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

class UnboxableExpression implements TargetWithContext {

  private Optional<NameDefinitions> defs = Optional.empty();
  private final ExpressionNode expression;
  private final String name;

  public UnboxableExpression(ExpressionNode expression) {
    this.expression = expression;
    name = String.format("Lift of %d:%d", expression.line(), expression.column());
  }

  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(names, predicate);
  }

  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public Flavour flavour() {
    return Flavour.LAMBDA;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void read() {
    // Super. Don't care.
  }

  public void render(Renderer renderer) {
    expression.render(renderer);
  }

  public String renderEcma(EcmaScriptRenderer renderer) {
    return expression.renderEcma(renderer);
  }

  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(this.defs.map(defs::withShadowContext).orElse(defs), errorHandler);
  }

  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public void setContext(NameDefinitions defs) {
    this.defs = Optional.of(defs);
  }

  @Override
  public Imyhat type() {
    return expression.type() instanceof Imyhat.OptionalImyhat
        ? ((Imyhat.OptionalImyhat) expression.type()).inner()
        : expression.type();
  }

  public boolean typeCheck(Consumer<String> errorHandler) {
    final var captureOk = expression.typeCheck(errorHandler);
    if (captureOk && !expression.type().isSame(expression.type().asOptional())) {
      expression.typeError("optional", expression.type(), errorHandler);
      return false;
    }
    return captureOk;
  }
}
