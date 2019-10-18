package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public final class GangElement {
  private final boolean dropIfEmpty;
  private final String name;
  private final Imyhat type;

  public GangElement(String name, Imyhat type, boolean dropIfEmpty) {
    this.name = name;
    this.type = type;
    this.dropIfEmpty = dropIfEmpty;
  }

  public boolean dropIfDefault() {
    return dropIfEmpty;
  }

  public String name() {
    return name;
  }

  public Imyhat type() {
    return type;
  }
}
