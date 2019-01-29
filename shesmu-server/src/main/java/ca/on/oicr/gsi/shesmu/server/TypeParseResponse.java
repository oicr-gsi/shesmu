package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;

public class TypeParseResponse {
  private String descriptor;
  private String humanName;

  public TypeParseResponse(Imyhat input) {
    humanName = input.name();
    descriptor = input.descriptor();
  }

  public String getDescriptor() {
    return descriptor;
  }

  public String getHumanName() {
    return humanName;
  }

  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  public void setHumanName(String humanName) {
    this.humanName = humanName;
  }
}
