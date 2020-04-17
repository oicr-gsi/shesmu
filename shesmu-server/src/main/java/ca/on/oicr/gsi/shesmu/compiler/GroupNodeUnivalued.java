package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A collection action that should only have a single value in a “Group” clause */
public final class GroupNodeUnivalued extends GroupNodeDefaultable {

  private final ExpressionNode expression;
  private final String name;
  private boolean read;

  public GroupNodeUnivalued(int line, int column, String name, ExpressionNode expression) {
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
    regroup.addUnivalued(expression.type(), name(), expression::render);
  }

  @Override
  public void render(Regrouper regroup, ExpressionNode initial, RootBuilder builder) {
    regroup.addUnivalued(expression.type(), name(), expression::render, initial::render);
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
    return expression.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return expression.typeCheck(errorHandler);
  }
}
