package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.List;

public class ActionFilterIds extends ActionFilter {
  private List<String> ids;

  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.ids(ids), filterBuilder);
  }

  public List<String> getIds() {
    return ids;
  }

  public void setIds(List<String> ids) {
    this.ids = ids;
  }
}
