package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;

public final class CommandRequest {
  private String command;
  private ActionFilter[] filters;

  public String getCommand() {
    return command;
  }

  public ActionFilter[] getFilters() {
    return filters;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }
}
