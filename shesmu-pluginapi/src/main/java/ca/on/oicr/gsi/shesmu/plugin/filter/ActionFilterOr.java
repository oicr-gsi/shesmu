package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

public class ActionFilterOr extends BaseCollectionActionFilter {

  @Override
  public <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder, Stream<F> filters) {
    return filterBuilder.or(filters);
  }
}
