package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;

public class LimsSequencingInfo {
  private final String id;
  private final boolean supplemental;
  private final boolean qcFailed;

  public LimsSequencingInfo(String id, boolean supplemental, boolean qcFailed) {
    super();
    this.id = id;
    this.supplemental = supplemental;
    this.qcFailed = qcFailed;
  }

  @ShesmuVariable
  public String id() {
    return id;
  }

  @ShesmuVariable
  public boolean supplemental() {
    return supplemental;
  }

  @ShesmuVariable
  public boolean qcFailed() {
    return qcFailed;
  }
}
