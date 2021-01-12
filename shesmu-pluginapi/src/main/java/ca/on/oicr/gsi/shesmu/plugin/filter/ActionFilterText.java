package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;

public class ActionFilterText extends ActionFilter {
  private boolean matchCase;
  private String text;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.textSearch(text, matchCase), filterBuilder);
  }

  public String getText() {
    return text;
  }

  public boolean isMatchCase() {
    return matchCase;
  }

  public void setMatchCase(boolean matchCase) {
    this.matchCase = matchCase;
  }

  public void setText(String text) {
    this.text = text;
  }
}
