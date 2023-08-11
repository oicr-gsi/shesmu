package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

/** An action filter that finds actions without active olives or <code>.actnow</code> files */
public class ActionFilterOrphaned extends ActionFilter {

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return filterBuilder.orphaned();
  }
}
