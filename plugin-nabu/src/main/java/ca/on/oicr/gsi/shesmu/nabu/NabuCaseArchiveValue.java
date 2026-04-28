package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NabuCaseArchiveValue extends NabuBaseArchiveValue {

  private final String case_identifier;
  private final Long requisition_id;
  private final String assay_name;
  private final String assay_version;
  private final Optional<Long> case_total_size;

  public NabuCaseArchiveValue(
      String archive_target,
      Set<String> archive_with,
      Optional<Instant> files_unloaded,
      String case_identifier,
      Optional<String> commvault_backup_job_id,
      Instant created,
      Optional<Instant> files_copied_to_offsite_archive_staging_dir,
      Optional<Instant> files_loaded_into_vidarr_archival,
      Set<String> lims_ids,
      ArchiveCaseMetadataDto metadata,
      Instant modified,
      boolean stop_processing,
      Long requisition_id,
      Set<String> workflow_run_ids_for_offsite_archive,
      Set<String> workflow_run_ids_for_vidarr_archival) {
    super(
        archive_target,
        archive_with,
        files_unloaded,
        commvault_backup_job_id,
        created,
        files_copied_to_offsite_archive_staging_dir,
        files_loaded_into_vidarr_archival,
        lims_ids,
        Optional.ofNullable(metadata.getArchiveNote()),
        Optional.ofNullable(metadata.getOffsiteArchiveSize()),
        Optional.ofNullable(metadata.getOnsiteArchiveSize()),
        modified,
        stop_processing,
        workflow_run_ids_for_offsite_archive,
        workflow_run_ids_for_vidarr_archival);
    this.requisition_id = requisition_id;
    this.case_identifier = case_identifier;
    this.assay_name = metadata.getAssayName();
    this.assay_version = metadata.getAssayVersion();
    this.case_total_size = Optional.ofNullable(metadata.getCaseTotalSize());
  }

  @ShesmuVariable
  public String case_identifier() {
    return case_identifier;
  }

  @ShesmuVariable
  public Long requisition_id() {
    return requisition_id;
  }

  @ShesmuVariable
  public String assay_name() {
    return assay_name;
  }

  @ShesmuVariable
  public String assay_version() {
    return assay_version;
  }

  @ShesmuVariable
  public Optional<Long> case_total_size() {
    return case_total_size;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), case_identifier);
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
    return super.equals(o)
        && case_identifier.equals(that.case_identifier)
        && requisition_id.equals(that.requisition_id);
  }
}
