package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NabuProjectArchiveValue {

  private final String archive_target;
  private final Set<String> archive_with;
  private final Optional<Instant> files_unloaded;
  private final String project_identifier;
  private final Optional<String> commvault_backup_job_id;
  private final Instant created;
  private final Optional<Instant> files_copied_to_offsite_archive_staging_dir;
  private final Optional<Instant> files_loaded_into_vidarr_archival;
  private final Set<String> lims_ids;
  private final Tuple metadata;
  private final Instant modified;
  private final long requisition_id;
  private final Set<String> workflow_run_ids_for_offsite_archive;
  private final Optional<Set<String>> workflow_run_ids_for_vidarr_archival;

  public NabuProjectArchiveValue(
      String archive_target,
      Set<String> archive_with,
      Optional<Instant> files_unloaded,
      String project_identifier,
      Optional<String> commvault_backup_job_id,
      Instant created,
      Optional<Instant> files_copied_to_offsite_archive_staging_dir,
      Optional<Instant> files_loaded_into_vidarr_archival,
      Set<String> lims_ids,
      Tuple metadata,
      Instant modified,
      long requisition_id,
      Set<String> workflow_run_ids_for_offsite_archive,
      Optional<Set<String>> workflow_run_ids_for_vidarr_archival) {
    super();
    this.archive_target = archive_target;
    this.archive_with = archive_with;
    this.files_unloaded = files_unloaded;
    this.project_identifier = project_identifier;
    this.commvault_backup_job_id = commvault_backup_job_id;
    this.created = created;
    this.files_copied_to_offsite_archive_staging_dir = files_copied_to_offsite_archive_staging_dir;
    this.files_loaded_into_vidarr_archival = files_loaded_into_vidarr_archival;
    this.lims_ids = lims_ids;
    this.metadata = metadata;
    this.modified = modified;
    this.requisition_id = requisition_id;
    this.workflow_run_ids_for_offsite_archive = workflow_run_ids_for_offsite_archive;
    this.workflow_run_ids_for_vidarr_archival = workflow_run_ids_for_vidarr_archival;
  }

  @ShesmuVariable
  public String archive_target() {
    return archive_target;
  }

  @ShesmuVariable
  public Set<String> archive_with() {
    return archive_with;
  }

  @ShesmuVariable
  public Optional<Instant> files_unloaded() {
    return files_unloaded;
  }

  @ShesmuVariable
  public String project_identifier() {
    return project_identifier;
  }

  @ShesmuVariable
  public Optional<String> commvault_backup_job_id() {
    return commvault_backup_job_id;
  }

  @ShesmuVariable
  public Instant created() {
    return created;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NabuProjectArchiveValue that = (NabuProjectArchiveValue) o;
    return archive_target.equals(that.archive_target())
        && archive_with.equals(that.archive_with())
        && files_unloaded == that.files_unloaded()
        && project_identifier.equals(that.project_identifier())
        && commvault_backup_job_id.equals(that.commvault_backup_job_id())
        && created.equals(that.created())
        && files_copied_to_offsite_archive_staging_dir
            == that.files_copied_to_offiste_archive_staging_dir()
        && files_loaded_into_vidarr_archival == that.files_loaded_into_vidarr_archival()
        && lims_ids.equals(that.lims_ids())
        && metadata.equals(that.metadata())
        && modified.equals(that.modified())
        && requisition_id == that.requisition_id()
        && workflow_run_ids_for_offsite_archive.equals(that.workflow_run_ids_for_offsite_archive())
        && workflow_run_ids_for_vidarr_archival.equals(that.workflow_run_ids_for_vidarr_archival());
  }

  @ShesmuVariable
  public Optional<Instant> files_copied_to_offiste_archive_staging_dir() {
    return files_copied_to_offsite_archive_staging_dir;
  }

  @ShesmuVariable
  public Optional<Instant> files_loaded_into_vidarr_archival() {
    return files_loaded_into_vidarr_archival;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        archive_target,
        archive_with,
        files_unloaded,
        project_identifier,
        commvault_backup_job_id,
        created,
        files_copied_to_offsite_archive_staging_dir,
        files_loaded_into_vidarr_archival,
        lims_ids,
        metadata,
        modified,
        requisition_id,
        workflow_run_ids_for_offsite_archive,
        workflow_run_ids_for_vidarr_archival);
  }

  @ShesmuVariable
  public Set<String> lims_ids() {
    return lims_ids;
  }

  @ShesmuVariable(
      type =
          "o6archive_note$qsassay_name$qsassay_version$qsproject_total_size$qioffsite_archive_size$qionsite_archive_size$qi")
  // If this object's size changes, the deserialization code needs to change as well
  public Tuple metadata() {
    return metadata;
  }

  @ShesmuVariable
  public Instant modified() {
    return modified;
  }

  @ShesmuVariable
  public long requisition_id() {
    return requisition_id;
  }

  @ShesmuVariable
  public Set<String> workflow_run_ids_for_offsite_archive() {
    return workflow_run_ids_for_offsite_archive;
  }

  @ShesmuVariable
  public Optional<Set<String>> workflow_run_ids_for_vidarr_archival() {
    return workflow_run_ids_for_vidarr_archival;
  }
}
