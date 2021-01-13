package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

public final class AlertFilterLabelName extends AlertFilter {
  private boolean isRegex;
  private String name;

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(isRegex ? f.hasLabelName(Pattern.compile(name)) : f.hasLabelName(name), f);
  }

  public String getName() {
    return name;
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
}
