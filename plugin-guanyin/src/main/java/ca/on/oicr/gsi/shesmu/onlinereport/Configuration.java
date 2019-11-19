package ca.on.oicr.gsi.shesmu.onlinereport;

import java.util.Map;

public class Configuration {
  private String cromwell;
  private String description;
  private String labelKey;
  private Map<String, String> parameters;
  private String workflowName;
  private String wdl;

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

  public String getWorkflowName() {
    return workflowName;
  }

  public String getWdl() {
    return wdl;
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

  public void setParameters(Map<String, String> parameters) {
    this.parameters = parameters;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  public void setWdl(String wdl) {
    this.wdl = wdl;
  }
}
