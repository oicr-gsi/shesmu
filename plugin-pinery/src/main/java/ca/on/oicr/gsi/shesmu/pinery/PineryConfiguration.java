package ca.on.oicr.gsi.shesmu.pinery;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PineryConfiguration {
  private String provider;
  private String shortProvider;
  private String url;
  private int version;

  public String getProvider() {
    return provider;
  }

  public String getShortProvider() {
    return shortProvider;
  }

  public String getUrl() {
    return url;
  }

  public int getVersion() {
    return version;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public void setShortProvider(String shortProvider) {
    this.shortProvider = shortProvider;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String shortProvider() {
    return shortProvider == null ? provider : shortProvider;
  }
}
