package ca.on.oicr.gsi.shesmu.cerberus;

import java.util.Map;

public class Configuration {

  private Map<String, PineryConfiguration> pinery;
  private Map<String, String> vidarr;

  public Map<String, PineryConfiguration> getPinery() {
    return pinery;
  }

  public Map<String, String> getVidarr() {
    return vidarr;
  }

  public void setPinery(Map<String, PineryConfiguration> pinery) {
    this.pinery = pinery;
  }

  public void setVidarr(Map<String, String> vidarr) {
    this.vidarr = vidarr;
  }
}
