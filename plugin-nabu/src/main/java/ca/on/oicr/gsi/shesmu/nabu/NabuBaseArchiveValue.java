package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class NabuBaseArchiveValue {

  protected final String archive_target;
  protected final Set<String> archive_with;
  protected final Optional<Instant> files_unloaded;
  protected final Optional<String> commvault_backup_job_id;
  protected final Instant created;
  protected final Optional<Instant> files_copied_to_offsite_archive_staging_dir;
  protected final Optional<Instant> files_loaded_into_vidarr_archival;
  protected final Set<String> lims_ids;
  protected final Optional<String> archive_note;
  protected final Optional<Long> offsite_archive_size;
  protected final Optional<Long> onsite_archive_size;
  protected final Instant modified;
  protected final Set<String> workflow_run_ids_for_offsite_archive;
  protected final Set<String> workflow_run_ids_for_vidarr_archival;

  protected NabuBaseArchiveValue(
      String archive_target,
      Set<String> archive_with,
      Optional<Instant> files_unloaded,
      Optional<String> commvault_backup_job_id,
      Instant created,
      Optional<Instant> files_copied_to_offsite_archive_staging_dir,
      Optional<Instant> files_loaded_into_vidarr_archival,
      Set<String> lims_ids,
      Optional<String> archive_note,
      Optional<Long> offsite_archive_size,
      Optional<Long> onsite_archive_size,
      Instant modified,
      Set<String> workflow_run_ids_for_offsite_archive,
      Set<String> workflow_run_ids_for_vidarr_archival) {
    this.archive_target = archive_target;
    this.archive_with = archive_with;
    this.files_unloaded = files_unloaded;
    this.commvault_backup_job_id = commvault_backup_job_id;
    this.created = created;
    this.files_copied_to_offsite_archive_staging_dir = files_copied_to_offsite_archive_staging_dir;
    this.files_loaded_into_vidarr_archival = files_loaded_into_vidarr_archival;
    this.lims_ids = lims_ids;
    this.archive_note = archive_note;
    this.offsite_archive_size = offsite_archive_size;
    this.onsite_archive_size = onsite_archive_size;
    this.modified = modified;
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
    NabuBaseArchiveValue that = (NabuBaseArchiveValue) o;
    return archive_target.equals(that.archive_target())
        && archive_with.equals(that.archive_with())
        && files_unloaded.equals(that.files_unloaded())
        && commvault_backup_job_id.equals(that.commvault_backup_job_id())
        && created.equals(that.created())
        && files_copied_to_offsite_archive_staging_dir.equals(
            that.files_copied_to_offsite_archive_staging_dir())
        && files_loaded_into_vidarr_archival.equals(that.files_loaded_into_vidarr_archival())
        && lims_ids.equals(that.lims_ids())
        && modified.equals(that.modified())
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
        workflow_run_ids_for_offsite_archive,
        workflow_run_ids_for_vidarr_archival);
  }

  @ShesmuVariable
  public Set<String> lims_ids() {
    return lims_ids;
  }

  @ShesmuVariable
  public Optional<String> archive_note() {
    return archive_note;
  }

  @ShesmuVariable
  public Optional<Long> offsite_archive_size() {
    return offsite_archive_size;
  }

  @ShesmuVariable
  public Optional<Long> onsite_archive_size() {
    return onsite_archive_size;
  }

  @ShesmuVariable
  public Instant modified() {
    return modified;
  }

  @ShesmuVariable
  public Set<String> workflow_run_ids_for_offsite_archive() {
    return workflow_run_ids_for_offsite_archive;
  }

  @ShesmuVariable
  public Set<String> workflow_run_ids_for_vidarr_archival() {
    return workflow_run_ids_for_vidarr_archival;
  }
}
