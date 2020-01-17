package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

public class ActionFilterText extends ActionFilter {
  private boolean matchCase;
  private String text;

  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(
        filterBuilder.textSearch(
            Pattern.compile(
                "^.*" + Pattern.quote(text) + ".*$", matchCase ? 0 : Pattern.CASE_INSENSITIVE)),
        filterBuilder);
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
