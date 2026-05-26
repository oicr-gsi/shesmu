package ca.on.oicr.gsi.shesmu.cerberus;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configuration {

  private Map<String, PineryConfiguration> pinery;
  private Map<String, String> vidarr;
  private List<String> ignore;
  private Set<String> excludeWorkflowsFromProvenance;

  public Map<String, PineryConfiguration> getPinery() {
    return pinery;
  }

  public Map<String, String> getVidarr() {
    return vidarr;
  }

  public List<String> getIgnore() {
    return ignore;
  }

  public Set<String> getExcludeWorkflowsFromProvenance() {
    return excludeWorkflowsFromProvenance;
  }

  public void setPinery(Map<String, PineryConfiguration> pinery) {
    this.pinery = pinery;
  }

  public void setVidarr(Map<String, String> vidarr) {
    this.vidarr = vidarr;
  }

  public void setIgnore(List<String> ignore) {
    this.ignore = ignore;
  }

  public void setExcludeWorkflowsFromProvenance(Set<String> excludeWorkflowsFromProvenance) {
    this.excludeWorkflowsFromProvenance = excludeWorkflowsFromProvenance;
  }
}
