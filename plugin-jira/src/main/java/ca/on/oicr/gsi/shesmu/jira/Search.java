package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;

public record Search(ActionFilter filter, String jql, String name, JoiningRule type) {}
