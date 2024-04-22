package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

/** Action filter that checks when an action's state last changed */
public class ActionFilterStatusChangedAgo extends BaseAgoActionFilter {
  @Override
  public <F> F convert(
      long offset, ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.statusChangedAgo(offset);
  }
}
