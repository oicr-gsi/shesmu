package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A flatten action in a “Group” clause
 *
 * <p>Also usable as the variable definition for the result
 */
public final class GroupNodeFlatten extends GroupNode {
  private final ExpressionNode expression;
  private Imyhat innerType = Imyhat.BAD;
  private final String name;
  private boolean read;

  public GroupNodeFlatten(int line, int column, String name, ExpressionNode expression) {
    super(line, column);
    this.name = name;
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
  public boolean isRead() {
    return read;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void read() {
    read = true;
  }

  @Override
  public void render(Regrouper regroup, RootBuilder rootBuilder) {
    regroup.addFlatten(innerType, name(), expression::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return expression.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return innerType.asList();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (expression.typeCheck(errorHandler)) {
      if (expression.type() instanceof Imyhat.ListImyhat) {
        innerType = ((Imyhat.ListImyhat) expression.type()).inner();
        return true;
      } else {
        expression.typeError("list", expression.type(), errorHandler);
      }
    }
    return false;
  }
}
