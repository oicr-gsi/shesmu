package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;

public class FilterRegex extends FilterJson {
  private String pattern;

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.textSearch(Pattern.compile(pattern)), filterBuilder);
  }

  public String getPattern() {
    return pattern;
  }

  public void setPattern(String pattern) {
    this.pattern = pattern;
  }
}
