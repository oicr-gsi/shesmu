package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

/** Utility filter for time-based filters anchored in the present */
public abstract class BaseAgoActionFilter extends ActionFilter {

  private long offset;

  /**
   * Convert the filter for the offset provided.
   *
   * @param offset the temporal offset
   * @param filterBuilder the filter builder
   * @return the constructed filter
   * @param <F> the filter type
   */
  protected abstract <F> F convert(
      long offset, ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder);

  @Override
  public final <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(convert(offset, filterBuilder), filterBuilder);
  }

  /**
   * Get the current temporal offset
   *
   * @return the offset in milliseconds
   */
  public final long getOffset() {
    return offset;
  }

  /**
   * Set the temporal offset
   *
   * @param offset the offset in milliseconds
   */
  public final void setOffset(long offset) {
    this.offset = offset;
  }
}
