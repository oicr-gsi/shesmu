package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

/** An action filter that requires all constituent filters be matched */
public class ActionFilterAnd extends BaseCollectionActionFilter {
  @Override
  public <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder, Stream<F> filters) {
    return filterBuilder.and(filters);
  }

  @Override
  protected String getOperation() {
    return "AND";
  }
}
