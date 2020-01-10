package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.List;

public class FilterIds extends FilterJson {
  private List<String> ids;

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.ids(ids), filterBuilder);
  }

  public List<String> getIds() {
    return ids;
  }

  public void setIds(List<String> ids) {
    this.ids = ids;
  }
}
