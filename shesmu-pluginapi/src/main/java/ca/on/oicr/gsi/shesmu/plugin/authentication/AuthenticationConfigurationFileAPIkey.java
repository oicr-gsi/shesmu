package ca.on.oicr.gsi.shesmu.plugin.authentication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class AuthenticationConfigurationFileAPIkey extends AuthenticationConfiguration {
  private String apikeyFile;

  public String getAPIkeyFile() {
    return apikeyFile;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return Files.readString(Paths.get(apikeyFile)).trim();
  }

  public void setAPIkeyFile(String apikeyFile) {
    this.apikeyFile = apikeyFile;
  }
}
