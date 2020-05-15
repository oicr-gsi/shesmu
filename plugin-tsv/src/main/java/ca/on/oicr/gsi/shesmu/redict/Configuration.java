package ca.on.oicr.gsi.shesmu.redict;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public class Configuration {
  private Imyhat key;
  private Imyhat value;

  public Imyhat getKey() {
    return key;
  }

  public Imyhat getValue() {
    return value;
  }

  public void setKey(Imyhat key) {
    this.key = key;
  }

  public void setValue(Imyhat value) {
    this.value = value;
  }
}
