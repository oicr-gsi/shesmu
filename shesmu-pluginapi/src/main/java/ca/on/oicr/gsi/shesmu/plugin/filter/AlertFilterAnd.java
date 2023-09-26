package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.List;

/** Alert filter that requires multiple filters be matched */
public final class AlertFilterAnd extends AlertFilter {
  private List<AlertFilter> filters = List.of();

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(f.and(filters.stream().map(filter -> filter.convert(f))), f);
  }

  /**
   * Gets the list of filters to match
   *
   * @return the list of filters
   */
  public List<AlertFilter> getFilters() {
    return filters;
  }
  /**
   * Sets the list of filters to match
   *
   * @param filters the list of filters
   */
  public void setFilters(List<AlertFilter> filters) {
    this.filters = filters;
  }
}
