package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NabuCaseArchiveValue {

  private final Optional<Instant> case_files_unloaded;
  private final String case_identifier;
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

  public NabuCaseArchiveValue(
      Optional<Instant> case_files_unloaded,
      String case_identifier,
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
    this.case_files_unloaded = case_files_unloaded;
    this.case_identifier = case_identifier;
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
  public Optional<Instant> case_files_unloaded() {
    return case_files_unloaded;
  }

  @ShesmuVariable
  public String case_identifier() {
    return case_identifier;
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
    NabuCaseArchiveValue that = (NabuCaseArchiveValue) o;
    return case_files_unloaded == that.case_files_unloaded()
        && case_identifier.equals(that.case_identifier())
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
        case_files_unloaded,
        case_identifier,
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
          "o5case_total_size$qioffsite_archive_size$qionsite_archive_size$qiassay_name$qsassay_version$qs")
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
