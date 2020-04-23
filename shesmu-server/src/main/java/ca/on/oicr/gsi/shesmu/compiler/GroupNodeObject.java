package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GroupNodeObject extends GroupNode {
  private final List<GroupNode> children;
  private final String name;

  public GroupNodeObject(int line, int column, String name, List<GroupNode> children) {
    super(line, column);
    this.name = name;
    this.children = children;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    for (final GroupNode child : children) {
      child.collectFreeVariables(names, predicate);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    for (final GroupNode child : children) {
      child.collectPlugins(pluginFileNames);
    }
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(Regrouper regroup, RootBuilder builder) {
    final Regrouper childGrouper =
        regroup.addObject(
            name, children.stream().map(child -> new Pair<>(child.name(), child.type())));
    for (final GroupNode child : children) {
      child.render(childGrouper, builder);
    }
  }

  @Override
  public boolean resolve(
      NameDefinitions defs, NameDefinitions outerDefs, Consumer<String> errorHandler) {
    final Map<String, Long> duplicates =
        children.stream().collect(Collectors.groupingBy(GroupNode::name, Collectors.counting()));
    boolean ok = true;
    for (final Map.Entry<String, Long> duplicate : duplicates.entrySet()) {
      if (duplicate.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Duplicate field “%s” in object.", line(), column(), duplicate.getKey()));
        ok = false;
      }
    }
    return ok
        & children.stream().filter(c -> c.resolve(defs, outerDefs, errorHandler)).count()
            == children.size();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return children
            .stream()
            .filter(c -> c.resolveDefinitions(expressionCompilerServices, errorHandler))
            .count()
        == children.size();
  }

  @Override
  public Imyhat type() {
    return new Imyhat.ObjectImyhat(
        children.stream().map(child -> new Pair<>(child.name(), child.type())));
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return children.stream().filter(c -> c.typeCheck(errorHandler)).count() == children.size();
  }
}
