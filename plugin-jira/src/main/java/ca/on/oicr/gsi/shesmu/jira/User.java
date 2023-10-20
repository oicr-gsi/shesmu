package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
  private String displayName;
  private String email;
  private String name;

  public String getDisplayName() {
    return displayName;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setName(String name) {
    this.name = name;
  }
}
