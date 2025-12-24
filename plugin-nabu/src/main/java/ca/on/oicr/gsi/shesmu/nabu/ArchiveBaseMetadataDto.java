package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ArchiveBaseMetadataDto {
  private String archiveNote;
  private Long offsiteArchiveSize;
  private Long onsiteArchiveSize;

  public String getArchiveNote() {
    return archiveNote;
  }

  public Long getOffsiteArchiveSize() {
    return offsiteArchiveSize;
  }

  public Long getOnsiteArchiveSize() {
    return onsiteArchiveSize;
  }

  public void setArchiveNote(String archiveNote) {
    this.archiveNote = archiveNote;
  }

  public void setOffsiteArchiveSize(Long offsiteArchiveSize) {
    this.offsiteArchiveSize = offsiteArchiveSize;
  }

  public void setOnsiteArchiveSize(Long onsiteArchiveSize) {
    this.onsiteArchiveSize = onsiteArchiveSize;
  }
}
