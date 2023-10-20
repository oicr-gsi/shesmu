package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class TransitionsResponse {
  private List<Transition> transitions;

  public List<Transition> getTransitions() {
    return transitions;
  }

  public void setTransitions(List<Transition> transitions) {
    this.transitions = transitions;
  }
}
