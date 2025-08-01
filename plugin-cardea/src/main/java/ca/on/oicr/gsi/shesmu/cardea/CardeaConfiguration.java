package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.authentication.AuthenticationConfiguration;

public class CardeaConfiguration {

  private AuthenticationConfiguration authentication;
  private String url;

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
}
