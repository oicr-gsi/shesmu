package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public class FilterExternalAgo extends AgoFilterJson {

  @Override
  public <F> F convert(
      Optional<Instant> start, Optional<Instant> end, FilterBuilder<F> filterBuilder) {
    return filterBuilder.external(start, end);
  }
}