package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("CASE")
public class NabuCaseArchiveDto extends NabuBaseArchiveDto {

  private String caseIdentifier;
  private long requisitionId;
  private ArchiveCaseMetadataDto metadata;

  public String getCaseIdentifier() {
    return caseIdentifier;
  }

  public ArchiveCaseMetadataDto getMetadata() {
    return metadata;
  }

  public long getRequisitionId() {
    return requisitionId;
  }

  public void setCaseIdentifier(String caseIdentifier) {
    this.caseIdentifier = caseIdentifier;
  }

  public void setMetadata(ArchiveCaseMetadataDto metadata) {
    this.metadata = metadata;
  }

  public void setRequisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }
}
