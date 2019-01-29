package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class GroupNodeWithDefault extends GroupNode {

  private final ExpressionNode initial;
  private final GroupNodeDefaultable inner;

  public GroupNodeWithDefault(
      int line, int column, ExpressionNode initial, GroupNodeDefaultable inner) {
    super(line, column);
    this.initial = initial;
    this.inner = inner;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    initial.collectFreeVariables(names, predicate);
    inner.collectFreeVariables(names, predicate);
  }

  @Override
  public String name() {
    return inner.name();
  }

  @Override
  public void render(Regrouper regroup, RootBuilder builder) {
    inner.render(regroup, initial, builder);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return inner.resolve(defs, outerDefs, errorHandler) & initial.resolve(outerDefs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler) {
    return inner.resolveDefinitions(definedOlives, definedFunctions, definedActions, errorHandler)
        & initial.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public Imyhat type() {
    return inner.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = inner.typeCheck(errorHandler) & initial.typeCheck(errorHandler);
    if (ok && !inner.type().isSame(initial.type())) {
      initial.typeError(inner.type().name(), initial.type(), errorHandler);
      ok = false;
    }
    return ok;
  }
}
