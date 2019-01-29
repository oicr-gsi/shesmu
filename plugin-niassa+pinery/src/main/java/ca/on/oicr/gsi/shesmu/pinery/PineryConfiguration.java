package ca.on.oicr.gsi.shesmu.pinery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PineryConfiguration {
  private String provider;
  private String url;
  private String version;

  public String getProvider() {
    return provider;
  }

  public String getUrl() {
    return url;
  }

  public String getVersion() {
    return version;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}
