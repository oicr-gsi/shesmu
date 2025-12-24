package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Set;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "entityType")
@JsonSubTypes({
  @JsonSubTypes.Type(value = NabuCaseArchiveDto.class, name = "CASE"),
  @JsonSubTypes.Type(value = NabuProjectArchiveDto.class, name = "PROJECT")
})
public abstract class NabuBaseArchiveDto {

  private String archiveTarget;
  private Set<String> archiveWith;
  private String filesUnloaded;
  private String commvaultBackupJobId;
  private String created;
  private String entityType;
  private String filesCopiedToOffsiteArchiveStagingDir;
  private String filesLoadedIntoVidarrArchival;
  private Set<String> limsIds;
  private String modified;
  private Set<String> workflowRunIdsForOffsiteArchive;
  private Set<String> workflowRunIdsForVidarrArchival;

  public String getArchiveTarget() {
    return archiveTarget;
  }

  public Set<String> getArchiveWith() {
    return archiveWith;
  }

  public String getFilesUnloaded() {
    return filesUnloaded;
  }

  public String getCommvaultBackupJobId() {
    return commvaultBackupJobId;
  }

  public String getCreated() {
    return created;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getFilesCopiedToOffsiteArchiveStagingDir() {
    return filesCopiedToOffsiteArchiveStagingDir;
  }

  public String getFilesLoadedIntoVidarrArchival() {
    return filesLoadedIntoVidarrArchival;
  }

  public Set<String> getLimsIds() {
    return limsIds;
  }

  public String getModified() {
    return modified;
  }

  public Set<String> getWorkflowRunIdsForOffsiteArchive() {
    return workflowRunIdsForOffsiteArchive;
  }

  public Set<String> getWorkflowRunIdsForVidarrArchival() {
    return workflowRunIdsForVidarrArchival;
  }

  public void setArchiveTarget(String archiveTarget) {
    this.archiveTarget = archiveTarget;
  }

  public void setArchiveWith(Set<String> archiveWith) {
    this.archiveWith = archiveWith;
  }

  public void setFilesUnloaded(String filesUnloaded) {
    this.filesUnloaded = filesUnloaded;
  }

  public void setCommvaultBackupJobId(String commvaultBackupJobId) {
    this.commvaultBackupJobId = commvaultBackupJobId;
  }

  public void setCreated(String created) {
    this.created = created;
  }

  public void setEntityType(String entityType) {
    this.entityType = entityType;
  }

  public void setFilesCopiedToOffsiteArchiveStagingDir(
      String filesCopiedToOffsiteArchiveStagingDir) {
    this.filesCopiedToOffsiteArchiveStagingDir = filesCopiedToOffsiteArchiveStagingDir;
  }

  public void setFilesLoadedIntoVidarrArchival(String filesLoadedIntoVidarrArchival) {
    this.filesLoadedIntoVidarrArchival = filesLoadedIntoVidarrArchival;
  }

  public void setLimsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  public void setModified(String modified) {
    this.modified = modified;
  }

  public void setWorkflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  public void setWorkflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }
}
