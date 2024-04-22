package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

/** Utility class for action filters that operate on a collection of other filters */
public abstract class BaseCollectionActionFilter extends ActionFilter {
  private ActionFilter[] filters;

  @Override
  public final <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(
        convert(
            filterBuilder, Stream.of(filters).map(filterJson -> filterJson.convert(filterBuilder))),
        filterBuilder);
  }

  /**
   * Convert the filter collection into a single filter
   *
   * @param filterBuilder the filter builder
   * @param filters the already converted constituent filters
   * @return the created filter
   * @param <F> the filter type
   */
  protected abstract <F> F convert(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder, Stream<F> filters);

  /**
   * Get the collection of constituent filters
   *
   * @return the constituent filters
   */
  public final ActionFilter[] getFilters() {
    return filters;
  }

  /**
   * Set the collection of constituent filters
   *
   * @param filters the constituent filters
   */
  public final void setFilters(ActionFilter[] filters) {
    this.filters = filters;
  }

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut
        .append("Collection Action Filter (")
        .append(getOperation())
        .append(" of: ")
        .append(Arrays.toString(filters));
    return writeOut.toString();
  }

  protected abstract String getOperation();
}
