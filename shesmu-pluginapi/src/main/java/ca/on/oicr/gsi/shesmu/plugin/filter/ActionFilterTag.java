package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

/** An action filter that checks if an action has a particular tag associated with it */
public class ActionFilterTag extends ActionFilter {
  private String[] tags;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.tags(Stream.of(tags)), filterBuilder);
  }

  /**
   * Gets the tags
   *
   * @return the tags to check
   */
  public String[] getTags() {
    return tags;
  }

  /**
   * Sets the tags
   *
   * @param tags the tags to check
   */
  public void setTags(String[] tags) {
    this.tags = tags;
  }

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut.append("Tag filter for tags: ").append(Arrays.toString(tags));
    return writeOut.toString();
  }
}
