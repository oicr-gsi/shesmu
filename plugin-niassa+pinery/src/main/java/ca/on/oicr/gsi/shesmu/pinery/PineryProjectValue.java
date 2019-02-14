package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.ws.dto.SampleProjectDto;

public class PineryProjectValue {
  private final SampleProjectDto backing;

  public PineryProjectValue(SampleProjectDto backing) {
    this.backing = backing;
  }

  @ShesmuVariable
  public boolean active() {
    return backing.isActive();
  }

  @ShesmuVariable
  public String name() {
    return backing.getName();
  }

  @ShesmuVariable
  public long sample_count() {
    return backing.getCount();
  }
}
