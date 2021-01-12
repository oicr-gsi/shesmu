package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.regex.Pattern;

public class ActionFilterTagRegex extends ActionFilter {
  private boolean matchCase;
  private String pattern;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(
        filterBuilder.tag(Pattern.compile(pattern, matchCase ? 0 : Pattern.CASE_INSENSITIVE)),
        filterBuilder);
  }

  public String getPattern() {
    return pattern;
  }

  public boolean isMatchCase() {
    return matchCase;
  }

  public void setMatchCase(boolean matchCase) {
    this.matchCase = matchCase;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }
}
