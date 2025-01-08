package ca.on.oicr.gsi.shesmu.cerberus;

import java.util.List;
import java.util.Map;

public class Configuration {

  private Map<String, PineryConfiguration> pinery;
  private Map<String, String> vidarr;
  private List<String> ignore;

  public Map<String, PineryConfiguration> getPinery() {
    return pinery;
  }

  public Map<String, String> getVidarr() {
    return vidarr;
  }

  public List<String> getIgnore() {
    return ignore;
  }

  public void setPinery(Map<String, PineryConfiguration> pinery) {
    this.pinery = pinery;
  }

  public void setVidarr(Map<String, String> vidarr) {
    this.vidarr = vidarr;
  }

  public void setVidarr(List<String> ignore) {
    this.ignore = ignore;
  }
}
