package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.*;
import ca.on.oicr.gsi.shesmu.plugin.action.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public class ArchiveProjectAction extends ArchiveAction<NabuProjectArchiveDto> {

  public ArchiveProjectAction(Definer<NabuPlugin> owner) {
    super(owner, "archive-project-action");
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

  @ActionParameter(name = "project_identifier")
  public void projectId(String projectId) {
    this.identifier = projectId;
  }

  @ActionParameter(name = "project_total_size")
  public void projectTotalSize(Long projectTotalSize) {
    this.totalSize = projectTotalSize;
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

  @ActionParameter(name = "workflow_run_ids_for_offsite_archive")
  public void workflowRunIdsForOffsiteArchive(Set<String> workflowRunIdsForOffsiteArchive) {
    this.workflowRunIdsForOffsiteArchive = workflowRunIdsForOffsiteArchive;
  }

  @ActionParameter(name = "workflow_run_ids_for_vidarr_archival")
  public void workflowRunIdsForVidarrArchival(Set<String> workflowRunIdsForVidarrArchival) {
    this.workflowRunIdsForVidarrArchival = workflowRunIdsForVidarrArchival;
  }

  @Override
  public ObjectNode parameters() {
    return parameters;
  }

  @Override
  protected String pathSegment() {
    return "/project";
  }

  @Override
  protected String identifierJsonFieldName() {
    return "projectIdentifier";
  }

  @Override
  protected String totalSizeJsonFieldName() {
    return "projectTotalSize";
  }

  @Override
  protected String entityLabel() {
    return "project";
  }

  @Override
  protected Class<NabuProjectArchiveDto[]> dtoArrayClass() {
    return NabuProjectArchiveDto[].class;
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
    final ArchiveProjectAction other = (ArchiveProjectAction) obj;
    if (!limsIds.equals(other.limsIds)) {
      return false;
    } else if (!parameters.equals(other.parameters)) {
      return false;
    }
    return identifier.equals(other.identifier);
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    try {
      digest.accept(MAPPER.writeValueAsBytes(owner));
      digest.accept(new byte[] {0});
      digest.accept(identifier.getBytes(StandardCharsets.UTF_8));
      digest.accept(new byte[] {0});
      digest.accept(MAPPER.writeValueAsBytes(limsIds));
      digest.accept(MAPPER.writeValueAsBytes(parameters));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(owner, parameters, identifier, limsIds);
  }
}
