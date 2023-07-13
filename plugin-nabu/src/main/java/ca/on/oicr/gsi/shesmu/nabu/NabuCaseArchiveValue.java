package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public class NabuCaseArchiveValue {
  private final Optional<Instant> caseFilesUnloaded;
  private final String caseIdentifier;
  private final Optional<String> commvaultBackupJobId;
  private final Instant created;
  private final Optional<Instant> filesCopiedToOffsiteArchiveStagingDir;
  private final Optional<Instant> filesLoadedIntoVidarrArchival;
  private final Set<String> limsIds;
  private final Instant modified;
  private final long requisitionId;
  private final Set<String> workflowRunIdsForOffsiteArchive;
  private final Optional<Set<String>> workflowRunIdsForVidarrArchival;

  public NabuCaseArchiveValue(
      Optional<Instant> caseFilesUnloaded,
      String caseIdentifier,
      Optional<String> commvaultBackupJobId,
      Instant created,
      Optional<Instant> filesCopiedToOffsiteArchiveStagingDir,
      Optional<Instant> filesLoadedIntoVidarrArchival,
      Set<String> limsIds,
      Instant modified,
      long requisitionId,
      Set<String> workflowRunIdsForOffsiteArchive,
      Optional<Set<String>> workflowRunIdsForVidarrArchival) {
    super();
    this.caseFilesUnloaded = caseFilesUnloaded;
    this.caseIdentifier = caseIdentifier;
    this.commvaultBackupJobId = commvaultBackupJobId;
    this.created = created;
    this.filesCopiedToOffsiteArchiveStagingDir = filesCopiedToOffsiteArchiveStagingDir;
    this.filesLoadedIntoVidarrArchival = filesLoadedIntoVidarrArchival;
    this.limsIds = limsIds;
    this.modified = modified;
    this.requisitionId = requisitionId;
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }

  @ShesmuVariable
  public Optional<Instant> caseFilesUnloaded() {
    return caseFilesUnloaded;
  }

  @ShesmuVariable
  public String caseIdentifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public Optional<String> commvaultBackupJobId() {
    return commvaultBackupJobId;
  }

  @ShesmuVariable
  public Instant created() {
    return created;
  }

  @ShesmuVariable
  public Optional<Instant> filesCopiedToOffsiteArchiveStagingDir() {
    return filesCopiedToOffsiteArchiveStagingDir;
  }

  @ShesmuVariable
  public Optional<Instant> filesLoadedIntoVidarrArchival() {
    return filesLoadedIntoVidarrArchival;
  }

  @ShesmuVariable
  public Set<String> limsIds() {
    return limsIds;
  }

  @ShesmuVariable
  public Instant modified() {
    return modified;
  }

  @ShesmuVariable
  public long requisitionId() {
    return requisitionId;
  }

  @ShesmuVariable
  public Set<String> workflowRunIdsForOffsiteArchive() {
    return workflowRunIdsForOffsiteArchive;
  }

  @ShesmuVariable
  public Optional<Set<String>> workflowRunIdsForVidarrArchival() {
    return workflowRunIdsForVidarrArchival;
  }
}
