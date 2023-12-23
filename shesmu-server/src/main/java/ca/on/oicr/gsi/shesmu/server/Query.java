package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/** Translate JSON-formatted queries into Java objects and perform the query */
public final class Query {

  private ActionFilter[] filters;

  private long limit = 100;

  private String sortBy;

  private long skip = 0;

  public ActionFilter[] getFilters() {
    return filters;
  }

  public long getLimit() {
    return limit;
  }

  public long getSkip() {
    return skip;
  }

  public String getSortBy() {
    return sortBy;
  }

  public Filter[] perform(ActionFilterBuilder<Filter, ActionState, String, Instant, Long> builder) {
    return Arrays.stream(getFilters())
        .filter(Objects::nonNull)
        .map(filterJson -> filterJson.convert(builder))
        .toArray(Filter[]::new);
  }

  public void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public void setSkip(long skip) {
    this.skip = skip;
  }

  public void setSortBy(String sortBy) {
    this.sortBy = sortBy;
  }
}
