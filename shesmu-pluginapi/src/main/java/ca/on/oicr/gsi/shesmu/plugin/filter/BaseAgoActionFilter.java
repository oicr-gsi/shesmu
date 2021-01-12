package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

public abstract class BaseAgoActionFilter extends ActionFilter {

  private long offset;

  protected abstract <F> F convert(
      long offset, ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder);

  @Override
  public final <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(convert(offset, filterBuilder), filterBuilder);
  }

  public final long getOffset() {
    return offset;
  }

  public final void setOffset(long offset) {
    this.offset = offset;
  }
}
