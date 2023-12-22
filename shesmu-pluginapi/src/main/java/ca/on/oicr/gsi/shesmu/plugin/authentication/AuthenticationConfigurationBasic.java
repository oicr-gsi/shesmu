package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AuthenticationConfigurationBasic extends AuthenticationConfiguration {
  public static String makeHeader(String username, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private String password;
  private String username;

  public String getPassword() {
    return password;
  }

  public String getUsername() {
    return username;
  }

  @Override
  public String prepareAuthentication() {
    return makeHeader(username, password);
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
