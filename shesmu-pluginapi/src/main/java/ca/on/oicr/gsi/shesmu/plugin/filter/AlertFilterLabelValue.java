package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

/** Alert filter that checks that a label is set to a particular value */
public final class AlertFilterLabelValue extends AlertFilter {
  private boolean isRegex;
  private String name;
  private String value;

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(
        isRegex ? f.hasLabelValue(name, Pattern.compile(value)) : f.hasLabelValue(name, value), f);
  }

  /**
   * The label name to match
   *
   * @return the label name
   */
  public String getName() {
    return name;
  }

  /**
   * The label value to match
   *
   * @return the label value or a regular expression
   */
  public String getValue() {
    return value;
  }

  /**
   * Whether the label value is a regular expression or a literal
   *
   * @return if true, the value is a regular expression; if false, a literal
   */
  public boolean isRegex() {
    return isRegex;
  }

  /**
   * Set the label name to match
   *
   * @param name the name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Sets whether the label value is a regular expression or a literal
   *
   * @param regex if true, the value is a regular expression; if false, a literal
   */
  public void setRegex(boolean regex) {
    isRegex = regex;
  }

  /**
   * Sets the label value or regular expression to match
   *
   * @param value the value or regular expression
   */
  public void setValue(String value) {
    this.value = value;
  }
}
