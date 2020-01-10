package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.time.Instant;
import java.util.Optional;

public abstract class AgoFilterJson extends FilterJson {

  private long offset;

  protected abstract <F> F convert(
      Optional<Instant> start, Optional<Instant> end, FilterBuilder<F> filterBuilder);

  @Override
  public final <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(
        convert(Optional.of(Instant.now().minusMillis(offset)), Optional.empty(), filterBuilder),
        filterBuilder);
  }

  public final long getOffset() {
    return offset;
  }

  public final void setOffset(long offset) {
    this.offset = offset;
  }
}
