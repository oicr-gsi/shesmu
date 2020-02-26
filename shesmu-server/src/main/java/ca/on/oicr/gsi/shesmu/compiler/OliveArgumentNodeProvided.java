package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The arguments defined in the “With” section of a “Run” olive. */
public final class OliveArgumentNodeProvided extends OliveArgumentNode {

  private final ExpressionNode expression;

  public OliveArgumentNodeProvided(
      int line, int column, DestructuredArgumentNode name, ExpressionNode expression) {
    super(line, column, name);
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  protected boolean isConditional() {
    return false;
  }

  /** Generate bytecode for this argument's value */
  @Override
  public void render(Renderer renderer, int action) {
    renderer.mark(line);
    storeAll(renderer, action, expression::render);
  }

  /** Resolve variables in the expression of this argument */
  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  /** Resolve functions in this argument */
  @Override
  public boolean resolveExtraFunctions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return expression.type();
  }

  /** Perform type check on this argument's expression */
  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
