package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.Collections;
import java.util.List;

public final class AlertFilterAnd extends AlertFilter {
  private List<AlertFilter> filters = Collections.emptyList();

  @Override
  public <F> F convert(AlertFilterBuilder<F> f) {
    return maybeNegate(f.and(filters.stream().map(filter -> filter.convert(f))), f);
  }

  public List<AlertFilter> getFilters() {
    return filters;
  }

  public void setFilters(List<AlertFilter> filters) {
    this.filters = filters;
  }
}
