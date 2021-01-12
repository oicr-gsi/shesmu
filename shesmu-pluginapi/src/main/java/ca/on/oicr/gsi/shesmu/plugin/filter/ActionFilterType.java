package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

public class ActionFilterType extends ActionFilter {
  private String[] types;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.type(Stream.of(types)), filterBuilder);
  }

  public String[] getTypes() {
    return types;
  }

  public void setTypes(String[] types) {
    this.types = types;
  }
}
