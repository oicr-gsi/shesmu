package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.io.IOException;

public final class AuthenticationConfigurationBearer extends AuthenticationConfiguration {
  private String token;

  public String getToken() {
    return token;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return "Bearer " + token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
