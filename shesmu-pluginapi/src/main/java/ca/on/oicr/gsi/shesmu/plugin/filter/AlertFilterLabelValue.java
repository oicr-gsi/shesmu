package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

public final class AlertFilterLabelValue extends AlertFilter {
  private boolean isRegex;
  private String name;
  private String value;

  @Override
  public <F> F convert(AlertFilterBuilder<F> f) {
    return maybeNegate(
        isRegex ? f.hasLabelValue(name, Pattern.compile(value)) : f.hasLabelValue(name, value), f);
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }

  public boolean isRegex() {
    return isRegex;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setRegex(boolean regex) {
    isRegex = regex;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
