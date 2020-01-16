package ca.on.oicr.gsi.shesmu.plugin.filter;

public class FilterStatusChangedAgo extends AgoFilterJson {
  @Override
  public <F> F convert(long offset, FilterBuilder<F> filterBuilder) {
    return filterBuilder.statusChangedAgo(offset);
  }
}
