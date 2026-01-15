package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("project")
public class NabuProjectArchiveDto extends NabuArchiveDto {

  private String projectIdentifier;

  public String getProjectIdentifier() {
    return projectIdentifier;
  }

  public void setProjectIdentifier(String projectIdentifier) {
    this.projectIdentifier = projectIdentifier;
  }
}
