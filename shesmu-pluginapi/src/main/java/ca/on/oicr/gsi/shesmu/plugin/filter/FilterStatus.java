package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;

public class FilterStatus extends FilterJson {
  private ActionState[] states;

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.isState(states), filterBuilder);
  }

  public ActionState[] getStates() {
    return states;
  }

  public void setState(ActionState[] states) {
    this.states = states;
  }
}
