package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.stream.Stream;

public class FilterTag extends FilterJson {
  private String[] tags;

  @Override
  public <F> F convert(FilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.tags(Stream.of(tags)), filterBuilder);
  }

  public String[] getTags() {
    return tags;
  }

  public void setTags(String[] tags) {
    this.tags = tags;
  }
}
