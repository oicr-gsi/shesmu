package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.ArrayList;
import java.util.List;

public final class DumperDefinition {
  private final List<Pair<String, Imyhat>> columns = new ArrayList<>();
  private final String name;

  public DumperDefinition(String name) {
    this.name = name;
  }

  void add(String name, Imyhat type) {
    columns.add(new Pair<>(name, type));
  }

  public void create(RootBuilder builder, Renderer renderer) {
    builder.createDumper(name, renderer, columns);
  }

  boolean isFresh() {
    return columns.isEmpty();
  }

  int size() {
    return columns.size();
  }

  Imyhat type(int index) {
    return columns.get(index).second();
  }
}
