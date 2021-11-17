package ca.on.oicr.gsi.shesmu.gsicommon;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.Gang;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface CerberusFileProvenanceSkippedValue {
  @ShesmuVariable
  String accession();

  @ShesmuVariable(signable = true)
  Optional<String> barcode_kit();

  @ShesmuVariable
  Set<String> batches();

  @ShesmuVariable(signable = true)
  Optional<Double> cell_viability();

  @ShesmuVariable
  Instant completed_date();

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 0),
        @Gang(name = "merged_library", order = 0)
      })
  String donor();

  @ShesmuVariable(signable = true)
  String external_donor_id();

  @ShesmuVariable(type = "o4id$sprovider$sstale$bversions$mss")
  Tuple external_key();

  @ShesmuVariable(signable = true)
  String external_tissue_id();

  @ShesmuVariable
  Map<String, Set<String>> file_attributes();

  @ShesmuVariable
  long file_size();

  @ShesmuVariable(signable = true)
  String group_desc();

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library", order = 4, dropIfDefault = true),
        @Gang(name = "merged_library_legacy", order = 4, dropIfDefault = true),
        @Gang(name = "merged_library_new", order = 3, dropIfDefault = true)
      })
  String group_id();

  @ShesmuVariable
  Set<String> input_files();

  @ShesmuVariable(signable = true)
  String instrument_model();

  @ShesmuVariable(type = "t3sis", signable = true)
  Tuple ius();

  @ShesmuVariable(signable = true)
  String kit();

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 3),
        @Gang(name = "merged_library", order = 3),
        @Gang(name = "merged_library_new", order = 2)
      })
  String library_design();

  @ShesmuVariable(signable = true)
  String library_name();

  @ShesmuVariable(signable = true)
  long library_size();

  @ShesmuVariable(signable = true)
  String library_type();

  @ShesmuVariable(type = "o4id$sprovider$stime$dversion$s")
  Tuple lims();

  @ShesmuVariable
  String md5();

  @ShesmuVariable
  String metatype();

  @ShesmuVariable(signable = true)
  String organism();

  @ShesmuVariable
  Path path();

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library_new", order = 0)})
  String project();

  @ShesmuVariable(signable = true)
  Optional<String> reference_slide_id();

  @ShesmuVariable(signable = true)
  String sequencing_control_type();

  @ShesmuVariable(signable = true)
  Optional<String> sex();

  @ShesmuVariable(signable = true)
  Optional<String> spike_in();

  @ShesmuVariable(signable = true)
  Optional<String> spike_in_dilution_factor();

  @ShesmuVariable(signable = true)
  Optional<Double> spike_in_volume_ul();

  /* todo: do we care about stale for skipped records?
  @ShesmuVariable
  boolean stale();
   */

  @ShesmuVariable(signable = true)
  Optional<String> subproject();

  @ShesmuVariable(signable = true)
  Optional<Double> target_cell_recovery();

  @ShesmuVariable(signable = true)
  String targeted_resequencing();

  @ShesmuVariable
  Instant timestamp();

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library_new", order = 1)})
  String tissue_name();

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 1),
        @Gang(name = "merged_library", order = 1)
      })
  String tissue_origin();

  @ShesmuVariable(signable = true)
  String tissue_prep();

  @ShesmuVariable(signable = true)
  String tissue_region();

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 2),
        @Gang(name = "merged_library", order = 2)
      })
  String tissue_type();

  @ShesmuVariable(signable = true)
  boolean umis();

  @ShesmuVariable
  String workflow();

  @ShesmuVariable
  String workflow_accession();

  @ShesmuVariable(type = "u2NIASSA$t0VIDARR$t0")
  AlgebraicValue workflow_engine();

  @ShesmuVariable
  String workflow_run_accession();

  @ShesmuVariable
  Map<String, Set<String>> workflow_run_attributes();

  @ShesmuVariable
  Map<String, JsonNode> workflow_run_labels();

  @ShesmuVariable(type = "t3iii")
  Tuple workflow_version();
}
