package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

public class ActionFilterStatus extends ActionFilter {
  private ActionState[] states;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.isState(Stream.of(states)), filterBuilder);
  }

  public ActionState[] getStates() {
    return states;
  }

  public void setState(ActionState[] states) {
    this.states = states;
  }
}
