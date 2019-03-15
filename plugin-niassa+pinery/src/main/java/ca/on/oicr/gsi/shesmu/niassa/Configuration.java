package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
  private String settings;
  private WorkflowConfiguration[] workflows;

  public String getSettings() {
    return settings;
  }

  public WorkflowConfiguration[] getWorkflows() {
    return workflows;
  }

  public void setSettings(String settings) {
    this.settings = settings;
  }

  public void setWorkflows(WorkflowConfiguration[] workflows) {
    this.workflows = workflows;
  }
}
