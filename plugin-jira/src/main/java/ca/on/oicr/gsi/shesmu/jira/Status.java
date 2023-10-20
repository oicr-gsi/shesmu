package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Status {
  private String description;
  private String id;
  private String name;

  public String getDescription() {
    return description;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }
}
