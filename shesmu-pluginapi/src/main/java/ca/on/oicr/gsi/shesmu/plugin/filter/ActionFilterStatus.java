package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;

public class ActionFilterStatus extends ActionFilter {
  private ActionState[] states;

  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.isState(states), filterBuilder);
  }

  public ActionState[] getStates() {
    return states;
  }

  public void setState(ActionState[] states) {
    this.states = states;
  }
}
