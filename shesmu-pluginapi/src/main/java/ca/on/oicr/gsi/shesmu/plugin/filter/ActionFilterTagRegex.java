package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.regex.Pattern;

/** An action filter that checks if an action has a tag that matches a regular expression */
public class ActionFilterTagRegex extends ActionFilter {
  private boolean matchCase;
  private String pattern;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(
        filterBuilder.tag(Pattern.compile(pattern, matchCase ? 0 : Pattern.CASE_INSENSITIVE)),
        filterBuilder);
  }

  /**
   * Get the regular expression
   *
   * @return the regular expression
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Check if the regular expression is case-sensitive
   *
   * @return true if case-sensitive
   */
  public boolean isMatchCase() {
    return matchCase;
  }

  /**
   * Sets if the regular expression is case-sensitive
   *
   * @param matchCase true if case-sensitive
   */
  public void setMatchCase(boolean matchCase) {
    this.matchCase = matchCase;
  }

  /**
   * Set the regular expression
   *
   * @param pattern the regular expression
   */
  public void setPattern(String pattern) {
    this.pattern = pattern;
  }
}
