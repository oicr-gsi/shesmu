package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class GroupNodeMatches extends GroupNode {
  private final ExpressionNode condition;
  private final Match matchType;
  private final String name;
  private boolean read;

  public GroupNodeMatches(
      int line, int column, String name, Match matchType, ExpressionNode condition) {
    super(line, column);
    this.name = name;
    this.matchType = matchType;
    this.condition = condition;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    condition.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    condition.collectPlugins(pluginFileNames);
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
  public void render(Regrouper regroup, RootBuilder builder) {
    regroup.addMatches(name, matchType, condition::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return condition.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return condition.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.BOOLEAN;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = condition.typeCheck(errorHandler);
    if (ok && !condition.type().isSame(Imyhat.BOOLEAN)) {
      condition.typeError(Imyhat.BOOLEAN, condition.type(), errorHandler);
      ok = false;
    }
    return ok;
  }
}
