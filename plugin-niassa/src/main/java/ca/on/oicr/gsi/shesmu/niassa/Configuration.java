package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
  private String cromwellUrl;
  private String prefix = "";
  private List<String> services = Collections.emptyList();
  private String settings;
  private String vidarrUrl;
  private String vidarrDbUrl;
  private String vidarrDbUser;
  private String vidarrDbPassword;
  private String vidarrOutputDirectory;

  public String getCromwellUrl() {
    return cromwellUrl;
  }

  public String getPrefix() {
    return prefix;
  }

  public List<String> getServices() {
    return services;
  }

  public String getSettings() {
    return settings;
  }

  public String getVidarrDbUrl() {
    return vidarrDbUrl;
  }

  public String getVidarrDbUser() {
    return vidarrDbUser;
  }

  public String getVidarrDbPassword() {
    return vidarrDbPassword;
  }

  public String getVidarrOutputDirectory() {
    return vidarrOutputDirectory;
  }

  public String getVidarrUrl() {
    return vidarrUrl;
  }

  public void setCromwellUrl(String cromwellUrl) {
    this.cromwellUrl = cromwellUrl;
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

  public void setVidarrDbUrl(String vidarrDbUrl) {
    this.vidarrDbUrl = vidarrDbUrl;
  }

  public void setVidarrDbUser(String vidarrDbUser) {
    this.vidarrDbUser = vidarrDbUser;
  }

  public void setVidarrDbPassword(String vidarrDbPassword) {
    this.vidarrDbPassword = vidarrDbPassword;
  }

  public void setVidarrOutputDirectory(String vidarrOutputDirectory) {
    this.vidarrOutputDirectory = vidarrOutputDirectory;
  }

  public void setVidarrUrl(String vidarrUrl) {
    this.vidarrUrl = vidarrUrl;
  }
}
