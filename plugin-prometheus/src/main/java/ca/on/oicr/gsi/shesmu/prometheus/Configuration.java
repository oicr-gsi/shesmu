package ca.on.oicr.gsi.shesmu.prometheus;

import java.util.List;

/** Bean of the on-disk configuration for a {@link PrometheusAlertManagerPluginType}. */
public class Configuration {
  private String alertmanager;
  private String environment;
  private List<String> labels = List.of("job");
  private int timeout;

  public String getAlertmanager() {
    return alertmanager;
  }

  public String getEnvironment() {
    return environment;
  }

  public List<String> getLabels() {
    return labels;
  }

  public void setAlertmanager(String alertmanager) {
    this.alertmanager = alertmanager;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public void setLabels(List<String> labels) {
    this.labels = labels;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
