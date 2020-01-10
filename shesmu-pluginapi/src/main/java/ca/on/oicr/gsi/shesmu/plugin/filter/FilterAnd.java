package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public class FilterAnd extends CollectionFilterJson {
  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder, Stream<F> filters) {
    return filterBuilder.and(filters);
  }
}
