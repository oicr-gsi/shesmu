package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;

public final class Search {
  private ActionFilter filter;
  private String jql;
  private String name;
  private JoiningRule type;

  public ActionFilter getFilter() {
    return filter;
  }

  public String getJql() {
    return jql;
  }

  public String getName() {
    return name;
  }

  public JoiningRule getType() {
    return type;
  }

  public void setFilter(ActionFilter filter) {
    this.filter = filter;
  }

  public void setJql(String jql) {
    this.jql = jql;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setType(JoiningRule type) {
    this.type = type;
  }
}
