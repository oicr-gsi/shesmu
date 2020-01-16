package ca.on.oicr.gsi.shesmu.plugin.filter;

public class FilterAddedAgo extends AgoFilterJson {
  @Override
  protected <F> F convert(long offset, FilterBuilder<F> filterBuilder) {
    return filterBuilder.addedAgo(offset);
  }
}
