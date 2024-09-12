package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.io.IOException;

public final class AuthenticationConfigurationAPIkey extends AuthenticationConfiguration {
  private String apikey;

  public String getAPIkey() {
    return apikey;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return apikey;
  }

  public void setAPIkey(String apikey) {
    this.apikey = apikey;
  }
}
