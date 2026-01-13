package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class NabuCaseArchiveValue extends NabuAbstractArchiveValue {

  private final String case_identifier;
  private final Tuple metadata;

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
      Tuple metadata,
      Instant modified,
      long requisition_id,
      Set<String> workflow_run_ids_for_offsite_archive,
      Optional<Set<String>> workflow_run_ids_for_vidarr_archival) {
    super(
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
    this.case_identifier = case_identifier;
    this.metadata = metadata;
  }

  @ShesmuVariable
  public String case_identifier() {
    return case_identifier;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), case_identifier, metadata);
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
        && metadata.equals(that.metadata);
  }

  @ShesmuVariable(
      type =
          "o6archive_note$qsassay_name$qsassay_version$qscase_total_size$qioffsite_archive_size$qionsite_archive_size$qi")
  // If this object's size changes, the deserialization code needs to change as well
  public Tuple metadata() {
    return metadata;
  }
}
