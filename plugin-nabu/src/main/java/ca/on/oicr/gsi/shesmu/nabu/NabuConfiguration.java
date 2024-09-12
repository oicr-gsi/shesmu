package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.authentication.AuthenticationConfiguration;

public class NabuConfiguration {

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
