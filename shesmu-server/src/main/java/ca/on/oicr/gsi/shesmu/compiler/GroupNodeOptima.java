package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class GroupNodeOptima extends GroupNodeDefaultable {

  private final ExpressionNode expression;
  private final boolean max;
  private final String name;

  public GroupNodeOptima(
      int line, int column, String name, ExpressionNode expression, boolean max) {
    super(line, column);
    this.name = name;
    this.expression = expression;
    this.max = max;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    expression.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(Regrouper regroup, ExpressionNode initial, RootBuilder builder) {
    regroup.addOptima(
        expression.type().apply(TypeUtils.TO_ASM),
        name(),
        max,
        expression::render,
        initial::render);
  }

  @Override
  public void render(Regrouper regroup, RootBuilder rootBuilder) {
    regroup.addOptima(expression.type().apply(TypeUtils.TO_ASM), name(), max, expression::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler) {
    return expression.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return expression.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (!expression.type().isOrderable()) {
        expression.typeError("orderable type", expression.type(), errorHandler);
        ok = false;
      }
    }
    return ok;
  }
}
