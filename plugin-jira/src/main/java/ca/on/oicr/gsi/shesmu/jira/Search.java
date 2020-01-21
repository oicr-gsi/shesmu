package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.filter.FilterJson;
import ca.on.oicr.gsi.shesmu.plugin.filter.JoiningRule;

public class Search {
  private FilterJson filter;
  private String jql;
  private String name;
  private JoiningRule type;

  public FilterJson getFilter() {
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

  public void setFilter(FilterJson filter) {
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
