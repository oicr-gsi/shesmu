package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public class ActionFilterAnd extends BaseCollectionActionFilter {
  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder, Stream<F> filters) {
    return filterBuilder.and(filters);
  }
}
