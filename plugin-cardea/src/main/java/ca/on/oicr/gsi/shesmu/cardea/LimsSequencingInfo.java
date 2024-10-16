package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;

public class LimsSequencingInfo {
  private final String id;
  private final boolean supplemental;

  public LimsSequencingInfo(String id, boolean supplemental) {
    super();
    this.id = id;
    this.supplemental = supplemental;
  }

  @ShesmuVariable
  public String id() {
    return id;
  }

  @ShesmuVariable
  public boolean supplemental() {
    return supplemental;
  }
}
