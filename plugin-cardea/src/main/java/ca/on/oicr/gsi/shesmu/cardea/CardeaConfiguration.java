package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.authentication.AuthenticationConfiguration;

public class CardeaConfiguration {

  private AuthenticationConfiguration authentication;
  private String url;
  private int timeout;

  public AuthenticationConfiguration getAuthentication() {
    return authentication;
  }

  public String getUrl() {
    return url;
  }

  public void setAuthentication(AuthenticationConfiguration authentication) {
    this.authentication = authentication;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
