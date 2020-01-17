package ca.on.oicr.gsi.shesmu.plugin.filter;

public class ActionFilterAddedAgo extends BaseAgoActionFilter {
  @Override
  protected <F> F convert(long offset, ActionFilterBuilder<F> filterBuilder) {
    return filterBuilder.addedAgo(offset);
  }
}
