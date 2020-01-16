package ca.on.oicr.gsi.shesmu.plugin.filter;

public abstract class AgoFilterJson extends FilterJson {

  private long offset;

  protected abstract <F> F convert(long offset, FilterBuilder<F> filterBuilder);

  @Override
  public final <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(convert(offset, filterBuilder), filterBuilder);
  }

  public final long getOffset() {
    return offset;
  }

  public final void setOffset(long offset) {
    this.offset = offset;
  }
}
