package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeOptionalUnbox extends ExpressionNode {

  private final ExpressionNode expression;

  private TargetWithContext target = Target.BAD;

  public ExpressionNodeOptionalUnbox(int line, int column, ExpressionNode expression) {
    super(line, column);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing; we rely on the context to process the expression
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing; we rely on the context to process the expression
  }

  @Override
  public void render(Renderer renderer) {
    renderer.mark(line());
    renderer.loadTarget(target);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    // We rely on the context to process the expression; however, we provide our context so that
    // caller can produce a better error message if a local variable is used.
    target.setContext(defs);
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    final Optional<TargetWithContext> candidate =
        expressionCompilerServices.captureOptional(expression);
    if (candidate.isPresent()) {
      target = candidate.get();
      return true;
    } else {
      errorHandler.accept(
          String.format("%d:%d: Optional operation “?” must be inside of ``.", line(), column()));
      return false;
    }
  }

  @Override
  public Imyhat type() {
    return target.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    // We rely on the context to process the expression
    return true;
  }
}