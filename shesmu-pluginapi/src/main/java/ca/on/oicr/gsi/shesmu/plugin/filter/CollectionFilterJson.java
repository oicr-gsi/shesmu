package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public abstract class CollectionFilterJson extends FilterJson {
  private FilterJson[] filters;

  @Override
  public final <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(
        convert(
            filterBuilder, Stream.of(filters).map(filterJson -> filterJson.convert(filterBuilder))),
        filterBuilder);
  }

  protected abstract <F> F convert(FilterBuilder<F> filterBuilder, Stream<F> filters);

  public final FilterJson[] getFilters() {
    return filters;
  }

  public final void setFilters(FilterJson[] filters) {
    this.filters = filters;
  }
}
