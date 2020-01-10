package ca.on.oicr.gsi.shesmu.plugin.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FilterAdded.class, name = "added"),
  @JsonSubTypes.Type(value = FilterAddedAgo.class, name = "addedago"),
  @JsonSubTypes.Type(value = FilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = FilterChecked.class, name = "checked"),
  @JsonSubTypes.Type(value = FilterCheckedAgo.class, name = "checkedago"),
  @JsonSubTypes.Type(value = FilterExternal.class, name = "external"),
  @JsonSubTypes.Type(value = FilterExternalAgo.class, name = "externalago"),
  @JsonSubTypes.Type(value = FilterIds.class, name = "id"),
  @JsonSubTypes.Type(value = FilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = FilterRegex.class, name = "regex"),
  @JsonSubTypes.Type(value = FilterSourceFile.class, name = "sourcefile"),
  @JsonSubTypes.Type(value = FilterSourceLocation.class, name = "sourcelocation"),
  @JsonSubTypes.Type(value = FilterStatus.class, name = "status"),
  @JsonSubTypes.Type(value = FilterStatusChanged.class, name = "statuschanged"),
  @JsonSubTypes.Type(value = FilterStatusChangedAgo.class, name = "statuschangedago"),
  @JsonSubTypes.Type(value = FilterTag.class, name = "tag"),
  @JsonSubTypes.Type(value = FilterText.class, name = "text"),
  @JsonSubTypes.Type(value = FilterType.class, name = "type")
})
public abstract class FilterJson {
  private boolean negate;

  public abstract <F> F convert(FilterBuilder<F> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, FilterBuilder<F> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
