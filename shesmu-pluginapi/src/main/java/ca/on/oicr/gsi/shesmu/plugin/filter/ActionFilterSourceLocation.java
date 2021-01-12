package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

public class ActionFilterSourceLocation extends ActionFilter {
  private SourceOliveLocation[] locations;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.fromSourceLocation(Stream.of(locations)), filterBuilder);
  }

  public SourceOliveLocation[] getLocations() {
    return locations;
  }

  public void setLocations(SourceOliveLocation[] locations) {
    this.locations = locations;
  }
}
