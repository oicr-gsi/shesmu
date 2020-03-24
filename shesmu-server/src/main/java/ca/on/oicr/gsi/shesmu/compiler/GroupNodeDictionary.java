package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class GroupNodeDictionary extends GroupNode {

  private final ExpressionNode key;
  private final ExpressionNode value;
  private final String name;

  public GroupNodeDictionary(
      int line, int column, String name, ExpressionNode key, ExpressionNode value) {
    super(line, column);
    this.name = name;
    this.key = key;
    this.value = value;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables, Predicate<Flavour> predicate) {
    key.collectFreeVariables(freeVariables, predicate);
    value.collectFreeVariables(freeVariables, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    key.collectPlugins(pluginFileNames);
    value.collectPlugins(pluginFileNames);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(Regrouper regroup, RootBuilder rootBuilder) {
    regroup.addCollected(key.type(), value.type(), name(), key::render, value::render);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return key.resolve(defs, errorHandler) & value.resolve(defs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return key.resolveDefinitions(expressionCompilerServices, errorHandler)
        & value.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return Imyhat.dictionary(key.type(), value.type());
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return key.typeCheck(errorHandler) & value.typeCheck(errorHandler);
  }
}
