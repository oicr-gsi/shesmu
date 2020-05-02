package ca.on.oicr.gsi.shesmu.loki;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.Map;

/** Bean for on-disk Loki service configuration files */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
  private Map<String, String> labels = Collections.emptyMap();
  private String url;

  public Map<String, String> getLabels() {
    return labels;
  }

  public String getUrl() {
    return url;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
