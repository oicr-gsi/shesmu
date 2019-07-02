package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** The arguments defined in the “With” section of a “Run” olive. */
public final class OliveArgumentNodeProvided extends OliveArgumentNode {

  private ActionParameterDefinition definition;
  private final ExpressionNode expression;

  public OliveArgumentNodeProvided(int line, int column, String name, ExpressionNode expression) {
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

  /** Produce an error if the type of the expression is not as required */
  @Override
  public boolean ensureType(ActionParameterDefinition definition, Consumer<String> errorHandler) {
    this.definition = definition;
    final boolean ok = definition.type().isSame(expression.type());
    if (!ok) {
      errorHandler.accept(
          String.format(
              "%d:%d: Expected argument “%s” to have type %s, but got %s.",
              line, column, name, definition.type().name(), expression.type().name()));
    }
    return ok;
  }

  /** Generate bytecode for this argument's value */
  @Override
  public void render(Renderer renderer, int action) {
    renderer.mark(line);
    definition.store(renderer, action, expression::render);
  }

  /** Resolve variables in the expression of this argument */
  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  /** Resolve functions in this argument */
  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
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
