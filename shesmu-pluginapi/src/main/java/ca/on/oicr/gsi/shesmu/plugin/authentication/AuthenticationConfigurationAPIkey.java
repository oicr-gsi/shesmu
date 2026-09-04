package ca.on.oicr.gsi.shesmu.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;

public final class AuthenticationConfigurationAPIkey extends AuthenticationConfiguration {
  private String apikey;

  @JsonProperty("apikey")
  public String getAPIkey() {
    return apikey;
  }

  @Override
  public String prepareAuthentication() throws IOException {
    return apikey;
  }

  @JsonProperty("apikey")
  public void setAPIkey(String apikey) {
    this.apikey = apikey;
  }
}
