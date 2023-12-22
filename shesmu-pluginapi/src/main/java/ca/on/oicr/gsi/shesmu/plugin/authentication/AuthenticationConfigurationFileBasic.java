package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class AuthenticationConfigurationFileBasic extends AuthenticationConfiguration {
  private String passwordFile;
  private String username;

  public String getPasswordFile() {
    return passwordFile;
  }

  public String getUsername() {
    return username;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return AuthenticationConfigurationBasic.makeHeader(
        username, Files.readString(Paths.get(passwordFile)).trim());
  }

  public void setPasswordFile(String passwordFile) {
    this.passwordFile = passwordFile;
  }

  public void setUsername(String username) {
    this.username = username;
  }
}
