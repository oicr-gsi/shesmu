package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Optional;

/** Action filter that checks when an action was last run by the scheduler */
public class ActionFilterChecked extends BaseRangeActionFilter {
  @Override
  public <F> F convert(
      Optional<Instant> start,
      Optional<Instant> end,
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.checked(start, end);
  }
}
