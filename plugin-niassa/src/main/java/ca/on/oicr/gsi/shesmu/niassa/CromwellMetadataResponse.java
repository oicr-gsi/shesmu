package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CromwellMetadataResponse {
  private Map<String, List<CromwellCall>> calls;
  private String workflowRoot;

  public Map<String, List<CromwellCall>> getCalls() {
    return calls;
  }

  public String getWorkflowRoot() {
    return workflowRoot;
  }

  public void setCalls(Map<String, List<CromwellCall>> calls) {
    this.calls = calls;
  }

  public void setWorkflowRoot(String workflowRoot) {
    this.workflowRoot = workflowRoot;
  }
}
