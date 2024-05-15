package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.TreeMap;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class TransitionRequest {
  private Map<String, JsonNode> fields = new TreeMap<>();
  private Transition transition;
  private Map<String, JsonNode> update = new TreeMap<>();

  public Map<String, JsonNode> getFields() {
    return fields;
  }

  public Transition getTransition() {
    return transition;
  }

  public Map<String, JsonNode> getUpdate() {
    return update;
  }

  public void setFields(Map<String, JsonNode> fields) {
    this.fields = fields;
  }

  public void setTransition(Transition transition) {
    this.transition = transition;
  }

  public void setUpdate(Map<String, JsonNode> update) {
    this.update = update;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder
        .append("TransitionRequest with fields: ")
        .append(fields)
        .append(", transition: ")
        .append(transition)
        .append(", update: ")
        .append(update);
    return builder.toString();
  }
}
