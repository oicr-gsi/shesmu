package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

/** Action filter that checks when an action was last run by the scheduler */
public class ActionFilterCheckedAgo extends BaseAgoActionFilter {
  @Override
  public <F> F convert(
      long offset, ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.checkedAgo(offset);
  }

  @Override
  protected String getOperation() {
    return "Last run by scheduler (ago)";
  }
}
