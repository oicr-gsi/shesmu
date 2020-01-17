package ca.on.oicr.gsi.shesmu.plugin.filter;

public class ActionFilterStatusChangedAgo extends BaseAgoActionFilter {
  @Override
  public <F> F convert(long offset, ActionFilterBuilder<F> filterBuilder) {
    return filterBuilder.statusChangedAgo(offset);
  }
}
