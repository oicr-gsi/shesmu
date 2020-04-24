package ca.on.oicr.gsi.shesmu.onlinereport;

import java.util.Map;

public class Configuration {
  private String cromwell;
  private String description;
  private String labelKey;
  private boolean pairsAsObjects;
  private Map<String, String> parameters;
  private String wdl;
  private String workflowName;

  public String getCromwell() {
    return cromwell;
  }

  public String getDescription() {
    return description;
  }

  public String getLabelKey() {
    return labelKey;
  }

  public Map<String, String> getParameters() {
    return parameters;
  }

  public String getWdl() {
    return wdl;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public boolean isPairsAsObjects() {
    return pairsAsObjects;
  }

  public void setCromwell(String cromwell) {
    this.cromwell = cromwell;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setLabelKey(String labelKey) {
    this.labelKey = labelKey;
  }

  public void setPairsAsObjects(boolean pairsAsObjects) {
    this.pairsAsObjects = pairsAsObjects;
  }

  public void setParameters(Map<String, String> parameters) {
    this.parameters = parameters;
  }

  public void setWdl(String wdl) {
    this.wdl = wdl;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }
}
