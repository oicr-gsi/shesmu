package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public class FilterAdded extends RangeFilterJson {
  @Override
  protected <F> F convert(
      Optional<Instant> start, Optional<Instant> end, FilterBuilder<F> filterBuilder) {
    return filterBuilder.added(start, end);
  }
}
