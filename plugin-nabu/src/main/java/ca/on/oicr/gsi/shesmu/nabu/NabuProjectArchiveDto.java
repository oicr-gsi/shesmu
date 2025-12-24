package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("PROJECT")
public class NabuProjectArchiveDto extends NabuBaseArchiveDto {

  private String projectIdentifier;
  private ArchiveProjectMetadataDto metadata;

  public ArchiveProjectMetadataDto getMetadata() {
    return metadata;
  }

  public String getProjectIdentifier() {
    return projectIdentifier;
  }

  public void setMetadata(ArchiveProjectMetadataDto metadata) {
    this.metadata = metadata;
  }

  public void setProjectIdentifier(String projectIdentifier) {
    this.projectIdentifier = projectIdentifier;
  }
}
