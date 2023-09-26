package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

/** An action filter that checks for actions in a particular state */
public class ActionFilterStatus extends ActionFilter {
  private ActionState[] states;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.isState(Stream.of(states)), filterBuilder);
  }

  /**
   * Gets the states to check
   *
   * @return the states
   */
  public ActionState[] getStates() {
    return states;
  }

  /**
   * Sets the states to check
   *
   * @param states the states
   */
  public void setState(ActionState[] states) {
    this.states = states;
  }
}
