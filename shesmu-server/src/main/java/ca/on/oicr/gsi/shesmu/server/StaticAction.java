package ca.on.oicr.gsi.shesmu.server;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class StaticAction {
  private String action;
  private ObjectNode parameters;

  public String getAction() {
    return action;
  }

  public ObjectNode getParameters() {
    return parameters;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public void setParameters(ObjectNode parameters) {
    this.parameters = parameters;
  }
}
