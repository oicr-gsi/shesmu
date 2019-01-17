package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.PartitionCount;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class GroupNodePartitionCount extends GroupNode {

  private final ExpressionNode condition;
  private final String name;

  public GroupNodePartitionCount(int line, int column, String name, ExpressionNode condition) {
    super(line, column);
    this.name = name;
    this.condition = condition;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    condition.collectFreeVariables(names, predicate);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(Regrouper regroup, RootBuilder builder) {
    regroup.addPartitionCount(name, condition::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return condition.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler) {
    return condition.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return PartitionCount.TYPE;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = condition.typeCheck(errorHandler);
    if (ok && !condition.type().isSame(Imyhat.BOOLEAN)) {
      condition.typeError(Imyhat.BOOLEAN.name(), condition.type(), errorHandler);
      ok = false;
    }
    return ok;
  }
}
