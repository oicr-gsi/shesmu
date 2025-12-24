package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchiveCaseMetadataDto extends ArchiveBaseMetadataDto {
  private Long caseTotalSize;
  private String assayName;
  private String assayVersion;

  public Long getCaseTotalSize() {
    return caseTotalSize;
  }

  public String getAssayName() {
    return assayName;
  }

  public String getAssayVersion() {
    return assayVersion;
  }

  public void setCaseTotalSize(Long caseTotalSize) {
    this.caseTotalSize = caseTotalSize;
  }

  public void setAssayName(String assayName) {
    this.assayName = assayName;
  }

  public void setAssayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }
}
