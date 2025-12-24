package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NabuProjectArchiveValue extends NabuBaseArchiveValue {

  private final String project_identifier;
  private final Optional<Long> project_total_size;

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
      ArchiveProjectMetadataDto metadata,
      Instant modified,
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
        workflow_run_ids_for_offsite_archive,
        workflow_run_ids_for_vidarr_archival);
    this.project_identifier = project_identifier;
    this.project_total_size = Optional.ofNullable(metadata.getProjectTotalSize());
  }

  @ShesmuVariable
  public String project_identifier() {
    return project_identifier;
  }

  @ShesmuVariable
  public Optional<Long> project_total_size() {
    return project_total_size;
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
    return super.equals(o) && project_identifier.equals(that.project_identifier);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), project_identifier);
  }
}
