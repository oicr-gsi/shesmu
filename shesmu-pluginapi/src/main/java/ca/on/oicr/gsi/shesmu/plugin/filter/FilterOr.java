package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public class FilterOr extends CollectionFilterJson {

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder, Stream<F> filters) {
    return filterBuilder.or(filters);
  }
}
