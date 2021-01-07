package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;

public class StatsRequest {
  private ActionFilter[] filters;
  private boolean wait;

  public ActionFilter[] getFilters() {
    return filters;
  }

  public boolean isWait() {
    return wait;
  }

  public void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }

  public void setWait(boolean wait) {
    this.wait = wait;
  }
}
