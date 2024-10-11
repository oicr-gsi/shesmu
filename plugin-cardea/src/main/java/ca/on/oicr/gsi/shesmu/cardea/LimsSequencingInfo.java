package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;

public class LimsSequencingInfo {
  private final String limsId;
  private final boolean supplemental;

  public LimsSequencingInfo(String limsId, boolean supplemental) {
    super();
    this.limsId = limsId;
    this.supplemental = supplemental;
  }

  @ShesmuVariable
  public String limsId() {
    return limsId;
  }

  @ShesmuVariable
  public boolean supplemental() {
    return supplemental;
  }
}
