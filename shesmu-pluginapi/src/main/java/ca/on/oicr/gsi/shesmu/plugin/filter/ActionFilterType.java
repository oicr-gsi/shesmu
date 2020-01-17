package ca.on.oicr.gsi.shesmu.plugin.filter;

public class ActionFilterType extends ActionFilter {
  private String[] types;

  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.type(types), filterBuilder);
  }

  public String[] getTypes() {
    return types;
  }

  public void setTypes(String[] types) {
    this.types = types;
  }
}
