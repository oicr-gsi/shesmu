package ca.on.oicr.gsi.shesmu.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.IOException;
import java.net.http.HttpRequest.Builder;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AuthenticationConfigurationBasic.class, name = "basic"),
  @JsonSubTypes.Type(value = AuthenticationConfigurationBearer.class, name = "bearer"),
  @JsonSubTypes.Type(value = AuthenticationConfigurationAPIkey.class, name = "apikey"),
  @JsonSubTypes.Type(value = AuthenticationConfigurationFileBasic.class, name = "basic-file"),
  @JsonSubTypes.Type(value = AuthenticationConfigurationFileBearer.class, name = "bearer-file"),
  @JsonSubTypes.Type(value = AuthenticationConfigurationFileAPIkey.class, name = "apikey-file")
})
public abstract sealed class AuthenticationConfiguration
    permits AuthenticationConfigurationBasic,
        AuthenticationConfigurationBearer,
        AuthenticationConfigurationAPIkey,
        AuthenticationConfigurationFileBasic,
        AuthenticationConfigurationFileBearer,
        AuthenticationConfigurationFileAPIkey {

  public static void addAuthenticationHeader(
      AuthenticationConfiguration authentication, Builder request) throws IOException {
    if (authentication != null) {
      request.header("Authorization", authentication.prepareAuthentication());
    }
  }

  public abstract String prepareAuthentication() throws IOException;
}
