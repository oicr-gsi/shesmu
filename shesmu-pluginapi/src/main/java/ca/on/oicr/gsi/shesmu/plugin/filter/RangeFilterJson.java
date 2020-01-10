package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public abstract class RangeFilterJson extends FilterJson {
  private Long end;

  private Long start;

  protected abstract <F> F convert(
      Optional<Instant> start, Optional<Instant> end, FilterBuilder<F> filterBuilder);

  @Override
  public final <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(
        convert(
            Optional.ofNullable(start).map(Instant::ofEpochMilli),
            Optional.ofNullable(end).map(Instant::ofEpochMilli),
            filterBuilder),
        filterBuilder);
  }

  public final Long getEnd() {
    return end;
  }

  public final Long getStart() {
    return start;
  }

  public final void setEnd(Long end) {
    this.end = end;
  }

  public final void setStart(Long start) {
    this.start = start;
  }
}
