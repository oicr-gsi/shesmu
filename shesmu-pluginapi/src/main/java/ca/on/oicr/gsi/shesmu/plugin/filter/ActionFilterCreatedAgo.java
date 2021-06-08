package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

public class ActionFilterCreatedAgo extends BaseAgoActionFilter {
  @Override
  public <F> F convert(
      long offset, ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.createdAgo(offset);
  }
}
