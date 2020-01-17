package ca.on.oicr.gsi.shesmu.plugin.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AlertFilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = AlertFilterIsLive.class, name = "is_live"),
  @JsonSubTypes.Type(value = AlertFilterLabelName.class, name = "has"),
  @JsonSubTypes.Type(value = AlertFilterLabelValue.class, name = "eq"),
  @JsonSubTypes.Type(value = AlertFilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = AlertFilterSourceLocation.class, name = "sourcelocation")
})
public abstract class AlertFilter {
  private boolean negate;

  public abstract <F> F convert(AlertFilterBuilder<F> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, AlertFilterBuilder<F> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
