package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class NabuAbstractArchiveValue {

  protected final String archive_target;
  protected final Set<String> archive_with;
  protected final Optional<Instant> files_unloaded;
  protected final Optional<String> commvault_backup_job_id;
  protected final Instant created;
  protected final Optional<Instant> files_copied_to_offsite_archive_staging_dir;
  protected final Optional<Instant> files_loaded_into_vidarr_archival;
  protected final Set<String> lims_ids;
  protected final Instant modified;
  protected final long requisition_id;
  protected final Set<String> workflow_run_ids_for_offsite_archive;
  protected final Optional<Set<String>> workflow_run_ids_for_vidarr_archival;

  protected NabuAbstractArchiveValue(
      String archive_target,
      Set<String> archive_with,
      Optional<Instant> files_unloaded,
      Optional<String> commvault_backup_job_id,
      Instant created,
      Optional<Instant> files_copied_to_offsite_archive_staging_dir,
      Optional<Instant> files_loaded_into_vidarr_archival,
      Set<String> lims_ids,
      Instant modified,
      long requisition_id,
      Set<String> workflow_run_ids_for_offsite_archive,
      Optional<Set<String>> workflow_run_ids_for_vidarr_archival) {
    this.archive_target = archive_target;
    this.archive_with = archive_with;
    this.files_unloaded = files_unloaded;
    this.commvault_backup_job_id = commvault_backup_job_id;
    this.created = created;
    this.files_copied_to_offsite_archive_staging_dir = files_copied_to_offsite_archive_staging_dir;
    this.files_loaded_into_vidarr_archival = files_loaded_into_vidarr_archival;
    this.lims_ids = lims_ids;
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
    NabuAbstractArchiveValue that = (NabuAbstractArchiveValue) o;
    return archive_target.equals(that.archive_target())
        && archive_with.equals(that.archive_with())
        && files_unloaded == that.files_unloaded()
        && commvault_backup_job_id.equals(that.commvault_backup_job_id())
        && created.equals(that.created())
        && files_copied_to_offsite_archive_staging_dir
            == that.files_copied_to_offsite_archive_staging_dir()
        && files_loaded_into_vidarr_archival == that.files_loaded_into_vidarr_archival()
        && lims_ids.equals(that.lims_ids())
        && modified.equals(that.modified())
        && requisition_id == that.requisition_id()
        && workflow_run_ids_for_offsite_archive.equals(that.workflow_run_ids_for_offsite_archive())
        && workflow_run_ids_for_vidarr_archival.equals(that.workflow_run_ids_for_vidarr_archival());
  }

  @ShesmuVariable
  public Optional<Instant> files_copied_to_offsite_archive_staging_dir() {
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
        commvault_backup_job_id,
        created,
        files_copied_to_offsite_archive_staging_dir,
        files_loaded_into_vidarr_archival,
        lims_ids,
        modified,
        requisition_id,
        workflow_run_ids_for_offsite_archive,
        workflow_run_ids_for_vidarr_archival);
  }

  @ShesmuVariable
  public Set<String> lims_ids() {
    return lims_ids;
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
