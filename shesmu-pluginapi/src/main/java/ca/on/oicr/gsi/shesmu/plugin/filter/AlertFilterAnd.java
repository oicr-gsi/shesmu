package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.List;

public final class AlertFilterAnd extends AlertFilter {
  private List<AlertFilter> filters = List.of();

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(f.and(filters.stream().map(filter -> filter.convert(f))), f);
  }

  public List<AlertFilter> getFilters() {
    return filters;
  }

  public void setFilters(List<AlertFilter> filters) {
    this.filters = filters;
  }
}
