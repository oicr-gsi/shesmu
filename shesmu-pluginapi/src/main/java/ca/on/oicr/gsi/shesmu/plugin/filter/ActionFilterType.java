package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

/** Action filter to check an action's type */
public class ActionFilterType extends ActionFilter {
  private String[] types;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.type(Stream.of(types)), filterBuilder);
  }

  /**
   * Gets the types to check
   *
   * @return the types
   */
  public String[] getTypes() {
    return types;
  }

  /**
   * Sets the types to check
   *
   * @param types the types
   */
  public void setTypes(String[] types) {
    this.types = types;
  }

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut.append("Action type filter of types: ").append(Arrays.toString(types));
    return writeOut.toString();
  }
}
