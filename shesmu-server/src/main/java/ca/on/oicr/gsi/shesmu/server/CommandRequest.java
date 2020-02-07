package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.filter.FilterJson;

public final class CommandRequest {
  private String command;
  private FilterJson[] filters;

  public String getCommand() {
    return command;
  }

  public FilterJson[] getFilters() {
    return filters;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setFilters(FilterJson[] filters) {
    this.filters = filters;
  }
}
