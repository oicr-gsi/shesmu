package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class GroupNodeWithDefault extends GroupNode {

  private final ExpressionNode initial;
  private final GroupNodeDefaultable inner;
  private Imyhat type = Imyhat.BAD;

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
  public void collectPlugins(Set<Path> pluginFileNames) {
    initial.collectPlugins(pluginFileNames);
    initial.collectPlugins(pluginFileNames);
  }

  @Override
  public boolean isRead() {
    return inner.isRead();
  }

  @Override
  public String name() {
    return inner.name();
  }

  @Override
  public void read() {
    inner.read();
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
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return inner.resolveDefinitions(expressionCompilerServices, errorHandler)
        & initial.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat type() {
    return type;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    var ok = inner.typeCheck(errorHandler) & initial.typeCheck(errorHandler);
    if (ok) {
      if (inner.type().isSame(initial.type())) {
        type = inner.type().unify(initial.type());
      } else {
        initial.typeError(inner.type(), initial.type(), errorHandler);
        ok = false;
      }
    }
    return ok;
  }
}
