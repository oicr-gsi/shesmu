package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Optional;

/** Action filter that checks an action's implementation-defined timestamp */
public class ActionFilterExternal extends BaseRangeActionFilter {
  @Override
  public <F> F convert(
      Optional<Instant> start,
      Optional<Instant> end,
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.external(start, end);
  }
}
