package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class AuthenticationConfigurationFileBearer extends AuthenticationConfiguration {
  private String tokenFile;

  public String getTokenFile() {
    return tokenFile;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return "Bearer " + Files.readString(Paths.get(tokenFile)).trim();
  }

  public void setTokenFile(String tokenFile) {
    this.tokenFile = tokenFile;
  }
}
