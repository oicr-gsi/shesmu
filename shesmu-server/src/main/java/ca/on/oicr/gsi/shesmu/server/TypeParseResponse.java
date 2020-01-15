package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;

public class TypeParseResponse {
  private String descriptor;
  private String humanName;
  private String wdlType;

  public TypeParseResponse(Imyhat input) {
    humanName = input.name();
    descriptor = input.descriptor();
    wdlType = input.apply(WdlInputType.TO_WDL_TYPE);
  }

  public String getDescriptor() {
    return descriptor;
  }

  public String getHumanName() {
    return humanName;
  }

  public String getWdlType() {
    return wdlType;
  }

  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  public void setHumanName(String humanName) {
    this.humanName = humanName;
  }

  public void setWdlType(String wdlType) {
    this.wdlType = wdlType;
  }
}
