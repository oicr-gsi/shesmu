package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Optional;

/** Utility filter for temporal range filters */
public abstract class BaseRangeActionFilter extends ActionFilter {
  private Long end;

  private Long start;

  /**
   * Convert a range to a filter
   *
   * @param start the start time of the range
   * @param end the end time of the range
   * @param filterBuilder the filter builder
   * @return the constructed filter
   * @param <F> the filter type
   */
  protected abstract <F> F convert(
      Optional<Instant> start,
      Optional<Instant> end,
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder);

  @Override
  public final <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(
        convert(
            Optional.ofNullable(start).map(Instant::ofEpochMilli),
            Optional.ofNullable(end).map(Instant::ofEpochMilli),
            filterBuilder),
        filterBuilder);
  }

  /**
   * Get the end time point of the range
   *
   * @return the time point in epoch milliseconds
   */
  public final Long getEnd() {
    return end;
  }

  /**
   * Get the start time point of the range
   *
   * @return the time point in epoch milliseconds
   */
  public final Long getStart() {
    return start;
  }

  /**
   * Set the end time point of the range
   *
   * @param end the time point in epoch milliseconds
   */
  public final void setEnd(Long end) {
    this.end = end;
  }
  /**
   * Set the start time point of the range
   *
   * @param start the time point in epoch milliseconds
   */
  public final void setStart(Long start) {
    this.start = start;
  }

  protected abstract String getName();

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut
        .append("Range filter of type: ")
        .append(getName())
        .append(" between ")
        .append(start)
        .append(" and ")
        .append(end);
    return writeOut.toString();
  }
}
