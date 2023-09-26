package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

/** Alert filter that matches alerts with a particular label name */
public final class AlertFilterLabelName extends AlertFilter {
  private boolean isRegex;
  private String name;

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(isRegex ? f.hasLabelName(Pattern.compile(name)) : f.hasLabelName(name), f);
  }

  /**
   * The name to match
   *
   * @return either the name or a regular expression
   */
  public String getName() {
    return name;
  }

  /**
   * Whether the name is really a regular expression for a name
   *
   * @return true if a regular expression; false if a literal
   */
  public boolean isRegex() {
    return isRegex;
  }

  /**
   * Sets the name or regular expression to match
   *
   * @param name the name or regular expression to match
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Sets whether the name should be interpreted as a regular expression
   *
   * @param regex true if a regular expression; false if a literal
   */
  public void setRegex(boolean regex) {
    isRegex = regex;
  }
}
