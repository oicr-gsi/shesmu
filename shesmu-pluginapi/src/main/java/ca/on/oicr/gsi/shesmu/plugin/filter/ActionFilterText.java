package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

/** An action filter that matches text in an action using a substring check */
public class ActionFilterText extends ActionFilter {
  private boolean matchCase;
  private String text;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.textSearch(text, matchCase), filterBuilder);
  }

  /**
   * Gets the text to match
   *
   * @return the text
   */
  public String getText() {
    return text;
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
   * Sets the text to match
   *
   * @param text the text
   */
  public void setText(String text) {
    this.text = text;
  }
}
