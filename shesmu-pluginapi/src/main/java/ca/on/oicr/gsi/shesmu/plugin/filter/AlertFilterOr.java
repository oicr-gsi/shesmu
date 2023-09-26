package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.List;

/** Alert filter that matches any one of the provided filters */
public final class AlertFilterOr extends AlertFilter {
  private List<AlertFilter> filters = List.of();

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(f.or(filters.stream().map(filter -> filter.convert(f))), f);
  }

  /**
   * Gets the list of filters to match
   *
   * @return the filters
   */
  public List<AlertFilter> getFilters() {
    return filters;
  }

  /**
   * Sets the list of filters to match
   *
   * @param filters the filters
   */
  public void setFilters(List<AlertFilter> filters) {
    this.filters = filters;
  }
}
