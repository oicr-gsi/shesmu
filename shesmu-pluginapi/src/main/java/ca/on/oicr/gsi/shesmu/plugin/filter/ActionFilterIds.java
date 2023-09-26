package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.List;

/** An action filter that checks action identifiers */
public class ActionFilterIds extends ActionFilter {
  private List<String> ids;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.ids(ids), filterBuilder);
  }

  /**
   * Gets the list of action identifiers this filter matches
   *
   * @return the action identifiers
   */
  public List<String> getIds() {
    return ids;
  }

  /**
   * Set the list of action identifiers this filter matches
   *
   * @param ids the action identifiers
   */
  public void setIds(List<String> ids) {
    this.ids = ids;
  }
}
