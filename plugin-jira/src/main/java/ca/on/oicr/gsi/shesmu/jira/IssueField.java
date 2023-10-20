package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class IssueField {
  private boolean hasDefaultValue;
  private String key;
  private boolean required;

  public String getKey() {
    return key;
  }

  public boolean isHasDefaultValue() {
    return hasDefaultValue;
  }

  public boolean isRequired() {
    return required;
  }

  public void setHasDefaultValue(boolean hasDefaultValue) {
    this.hasDefaultValue = hasDefaultValue;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }
}
