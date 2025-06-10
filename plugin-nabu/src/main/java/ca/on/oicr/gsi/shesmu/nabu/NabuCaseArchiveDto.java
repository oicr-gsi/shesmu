package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NabuCaseArchiveDto {

  private String archiveNote;
  private String archiveTarget;
  private Set<String> archiveWith;
  private String caseFilesUnloaded;
  private String caseIdentifier;
  private String commvaultBackupJobId;
  private String created;
  private String filesCopiedToOffsiteArchiveStagingDir;
  private String filesLoadedIntoVidarrArchival;
  private Set<String> limsIds;
  private ObjectNode metadata;
  private String modified;
  private long requisitionId;
  private Set<String> workflowRunIdsForOffsiteArchive;
  private Set<String> workflowRunIdsForVidarrArchival;

  public String getArchiveNote() {
    return archiveNote;
  }

  public String getArchiveTarget() {
    return archiveTarget;
  }

  public Set<String> getArchiveWith() {
    return archiveWith;
  }

  public String getCaseFilesUnloaded() {
    return caseFilesUnloaded;
  }

  public String getCaseIdentifier() {
    return caseIdentifier;
  }

  public String getCommvaultBackupJobId() {
    return commvaultBackupJobId;
  }

  public String getCreated() {
    return created;
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

  public ObjectNode getMetadata() {
    return metadata;
  }

  public String getModified() {
    return modified;
  }

  public long getRequisitionId() {
    return requisitionId;
  }

  public Set<String> getWorkflowRunIdsForOffsiteArchive() {
    return workflowRunIdsForOffsiteArchive;
  }

  public Set<String> getWorkflowRunIdsForVidarrArchival() {
    return workflowRunIdsForVidarrArchival;
  }

  public void setArchiveNote(String archiveNote) {
    this.archiveNote = archiveNote;
  }

  public void setArchiveTarget(String archiveTarget) {
    this.archiveTarget = archiveTarget;
  }

  public void setArchiveWith(Set<String> archiveWith) {
    this.archiveWith = archiveWith;
  }

  public void setCaseFilesUnloaded(String caseFilesUnloaded) {
    this.caseFilesUnloaded = caseFilesUnloaded;
  }

  public void setCaseIdentifier(String caseIdentifier) {
    this.caseIdentifier = caseIdentifier;
  }

  public void setCommvaultBackupJobId(String commvaultBackupJobId) {
    this.commvaultBackupJobId = commvaultBackupJobId;
  }

  public void setCreated(String created) {
    this.created = created;
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

  public void setMetadata(ObjectNode metadata) {
    this.metadata = metadata;
  }

  public void setModified(String modified) {
    this.modified = modified;
  }

  public void setRequisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }

  public void setWorkflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  public void setWorkflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }
}
