package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
  private String prefix = "";
  private List<String> services = Collections.emptyList();
  private String settings;

  public String getPrefix() {
    return prefix;
  }

  public List<String> getServices() {
    return services;
  }

  public String getSettings() {
    return settings;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setServices(List<String> services) {
    this.services = services;
  }

  public void setSettings(String settings) {
    this.settings = settings;
  }
}
