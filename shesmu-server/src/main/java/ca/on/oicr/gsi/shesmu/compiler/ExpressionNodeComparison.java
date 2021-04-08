package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeComparison extends ExpressionNode {

  private final Comparison comparison;
  private final ExpressionNode left;
  private final ExpressionNode right;

  public ExpressionNodeComparison(
      int line, int column, Comparison comparison, ExpressionNode left, ExpressionNode right) {
    super(line, column);
    this.comparison = comparison;
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

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    return left.type()
        .unify(right.type())
        .apply(
            comparison.render(
                renderer,
                renderer.newConst(left.renderEcma(renderer)),
                renderer.newConst(right.renderEcma(renderer))));
  }

  @Override
  public void render(Renderer renderer) {
    left.render(renderer);
    right.render(renderer);
    renderer.mark(line());

    final var end = renderer.methodGen().newLabel();
    final var truePath = renderer.methodGen().newLabel();
    if (left.type().isSame(Imyhat.BOOLEAN)) {
      comparison.branchBool(truePath, renderer.methodGen());
    } else if (left.type().isSame(Imyhat.INTEGER)) {
      comparison.branchInt(truePath, renderer.methodGen());
    } else if (left.type().isSame(Imyhat.FLOAT)) {
      comparison.branchFloat(truePath, renderer.methodGen());
    } else if (left.type().isOrderable()) {
      comparison.branchComparable(truePath, renderer.methodGen());
    } else {
      comparison.branchObject(truePath, renderer.methodGen());
    }
    renderer.methodGen().push(false);
    renderer.methodGen().goTo(end);
    renderer.methodGen().mark(truePath);
    renderer.methodGen().push(true);
    renderer.methodGen().mark(end);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return left.resolve(defs, errorHandler) & right.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return left.resolveDefinitions(expressionCompilerServices, errorHandler)
        & right.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final var ok = left.typeCheck(errorHandler) & right.typeCheck(errorHandler);
    if (ok) {
      // This logic is a bit funky because in the case of an algebraic type FOO
      // == BAR should be an invalid comparison, but because they are both ADT,
      // they are considered mergable (isSame). So, we check that either side
      // is a subset, so if x is FOO or BAR, then x == FOO || FOO == x is
      // valid, but x == BAZ || BAZ == x is not.
      if (!left.type().isAssignableFrom(right.type())
          && !right.type().isAssignableFrom(left.type())) {
        typeError(left.type(), right.type(), errorHandler);
        return false;
      }
      if (comparison.isOrdered() && !left.type().isOrderable()) {
        errorHandler.accept(
            String.format(
                "%d:%d: Comparison %s not defined for type %s.",
                line(), column(), comparison.symbol(), left.type().name()));
        return false;
      }
    }
    return ok;
  }
}
