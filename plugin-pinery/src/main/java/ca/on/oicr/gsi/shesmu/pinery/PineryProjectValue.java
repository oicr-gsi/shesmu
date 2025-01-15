package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.ws.dto.SampleProjectDto;
import java.util.Optional;
import java.util.Set;

public class PineryProjectValue {
  private final SampleProjectDto backing;
  private final String provider;

  public PineryProjectValue(SampleProjectDto backing, String provider) {
    this.backing = backing;
    this.provider = provider;
  }

  @ShesmuVariable
  public boolean active() {
    return backing.isActive();
  }

  @ShesmuVariable
  public Optional<Set<String>> deliverables() {
    return Optional.ofNullable(backing.getDeliverables());
  }

  @ShesmuVariable
  public String name() {
    return backing.getName();
  }

  @ShesmuVariable
  public String pipeline() {
    return backing.getPipeline();
  }

  @ShesmuVariable
  public String provider() {
    return provider;
  }

  @ShesmuVariable
  public long sample_count() {
    return backing.getCount();
  }
}
