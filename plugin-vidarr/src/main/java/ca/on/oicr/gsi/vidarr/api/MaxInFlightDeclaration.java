package ca.on.oicr.gsi.vidarr.api;

import java.util.Map;

public final class MaxInFlightDeclaration {

  private long timestamp;
  private Map<String, InFlightValue> workflows;

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public Map<String, InFlightValue> getWorkflows() {
    return workflows;
  }

  public void setWorkflows(Map<String, InFlightValue> workflows) {
    this.workflows = workflows;
  }
}
