package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public class ActionFilterAdded extends BaseRangeActionFilter {
  @Override
  protected <F> F convert(
      Optional<Instant> start, Optional<Instant> end, ActionFilterBuilder<F> filterBuilder) {
    return filterBuilder.added(start, end);
  }
}
