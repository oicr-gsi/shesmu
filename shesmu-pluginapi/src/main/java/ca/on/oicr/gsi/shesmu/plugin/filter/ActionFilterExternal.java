package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public class ActionFilterExternal extends BaseRangeActionFilter {
  @Override
  public <F> F convert(
      Optional<Instant> start, Optional<Instant> end, ActionFilterBuilder<F> filterBuilder) {
    return filterBuilder.external(start, end);
  }
}
