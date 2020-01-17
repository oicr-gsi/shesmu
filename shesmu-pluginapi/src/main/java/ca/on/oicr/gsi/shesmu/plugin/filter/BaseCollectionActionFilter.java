package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public abstract class BaseCollectionActionFilter extends ActionFilter {
  private ActionFilter[] filters;

  @Override
  public final <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(
        convert(
            filterBuilder, Stream.of(filters).map(filterJson -> filterJson.convert(filterBuilder))),
        filterBuilder);
  }

  protected abstract <F> F convert(ActionFilterBuilder<F> filterBuilder, Stream<F> filters);

  public final ActionFilter[] getFilters() {
    return filters;
  }

  public final void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }
}
