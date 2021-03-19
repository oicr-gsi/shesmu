package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class GroupNodeWhere extends GroupNode {

  private final ExpressionNode condition;
  private final GroupNode sink;

  public GroupNodeWhere(int line, int column, ExpressionNode condition, GroupNode sink) {
    super(line, column);
    this.condition = condition;
    this.sink = sink;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    condition.collectFreeVariables(names, predicate);
    sink.collectFreeVariables(names, predicate);
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    condition.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isRead() {
    return sink.isRead();
  }

  @Override
  public String name() {
    return sink.name();
  }

  @Override
  public void read() {
    sink.read();
  }

  @Override
  public void render(Regrouper regroup, RootBuilder builder) {
    sink.render(regroup.addWhere(condition::render), builder);
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    return condition.resolve(defs, errorHandler) & sink.resolve(defs, outerDefs, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return condition.resolveDefinitions(expressionCompilerServices, errorHandler)
        & sink.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return sink.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = condition.typeCheck(errorHandler);
    if (ok && !condition.type().isSame(Imyhat.BOOLEAN)) {
      condition.typeError(Imyhat.BOOLEAN, condition.type(), errorHandler);
      ok = false;
    }
    return ok & sink.typeCheck(errorHandler);
  }
}
