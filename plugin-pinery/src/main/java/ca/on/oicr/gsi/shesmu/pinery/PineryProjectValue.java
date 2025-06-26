package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.ws.dto.DeliverableDto;
import ca.on.oicr.ws.dto.SampleProjectDto;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    Set<String> names =
        backing.getDeliverables().stream().map(DeliverableDto::getName).collect(Collectors.toSet());
    if (names.isEmpty()) return Optional.empty();
    return Optional.of(names);
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
