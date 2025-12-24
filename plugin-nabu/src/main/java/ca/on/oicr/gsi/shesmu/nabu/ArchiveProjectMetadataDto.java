package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchiveProjectMetadataDto extends ArchiveBaseMetadataDto {
  private Long projectTotalSize;

  public Long getProjectTotalSize() {
    return projectTotalSize;
  }

  public void setProjectTotalSize(Long projectTotalSize) {
    this.projectTotalSize = projectTotalSize;
  }
}
