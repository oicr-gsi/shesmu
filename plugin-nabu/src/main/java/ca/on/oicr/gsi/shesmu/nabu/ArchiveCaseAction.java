package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class ArchiveCaseAction extends ArchiveAction<NabuCaseArchiveDto> {

  public ArchiveCaseAction(Definer<NabuPlugin> owner) {
    super(owner, "archive-case-action");
  }

  @ActionParameter(name = "assay_name")
  public void assayName(String assayName) {
    this.assayName = assayName;
  }

  @ActionParameter(name = "assay_version")
  public void assayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }

  @ActionParameter(name = "archive_note")
  public void archiveNote(Optional<String> archiveNote) {
    this.archiveNote = archiveNote;
  }

  @ActionParameter(name = "archive_target")
  public void archiveTarget(String archiveTarget) {
    this.archiveTarget = archiveTarget;
  }

  @ActionParameter(name = "archive_with")
  public void archiveWith(Set<String> archiveWith) {
    this.archiveWith = archiveWith;
  }

  @ActionParameter(name = "case_identifier")
  public void caseId(String caseId) {
    this.identifier = caseId;
  }

  @ActionParameter(name = "case_total_size")
  public void caseTotalSize(Long caseTotalSize) {
    this.totalSize = caseTotalSize;
  }

  @ActionParameter(name = "lims_ids")
  public void limsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  @ActionParameter(name = "offsite_archive_size")
  public void offsiteArchiveSize(Long offsiteArchiveSize) {
    this.offsiteArchiveSize = offsiteArchiveSize;
  }

  @ActionParameter(name = "onsite_archive_size")
  public void onsiteArchiveSize(Long onsiteArchiveSize) {
    this.onsiteArchiveSize = onsiteArchiveSize;
  }

  @ActionParameter(name = "requisition_id")
  public void requisitionId(Optional<Long> requisitionId) {
    this.requisitionId = requisitionId;
  }

  @ActionParameter(name = "workflow_run_ids_for_offsite_archive")
  public void workflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  @ActionParameter(name = "workflow_run_ids_for_vidarr_archival")
  public void workflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }

  @Override
  protected String identifierJsonFieldName() {
    return "caseIdentifier";
  }

  @Override
  protected String totalSizeJsonFieldName() {
    return "caseTotalSize";
  }

  @Override
  protected String entityLabel() {
    return "case";
  }

  @Override
  protected Class<NabuCaseArchiveDto[]> dtoArrayClass() {
    return NabuCaseArchiveDto[].class;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ArchiveCaseAction other = (ArchiveCaseAction) obj;
    return Objects.equals(this.requisitionId, other.requisitionId)
        && Objects.equals(this.limsIds, other.limsIds)
        && Objects.equals(this.parameters, other.parameters)
        && Objects.equals(this.identifier, other.identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, parameters, identifier, limsIds, requisitionId);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(identifier.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      digest.accept(MAPPER.writeValueAsBytes(requisitionId));
      digest.accept(MAPPER.writeValueAsBytes(limsIds));
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }
}
