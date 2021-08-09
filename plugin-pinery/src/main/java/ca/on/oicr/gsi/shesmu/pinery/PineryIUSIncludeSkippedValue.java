package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** IUS information from Pinery, including skipped samples */
public final class PineryIUSIncludeSkippedValue extends PineryIUSValue {

  private boolean skip;

  @ShesmuVariable
  public boolean skip() {
    return skip;
  }

  public PineryIUSIncludeSkippedValue(
      Optional<String> barcode_kit,
      String bases_mask,
      Set<String> batches,
      Optional<Double> cell_viability,
      Optional<Instant> completed_date,
      Optional<String> container_model,
      String donor,
      Optional<Double> dv200,
      String external_donor_id,
      Tuple external_key,
      String external_tissue_id,
      Set<Set<Long>> flowcellGeometry,
      String group_desc,
      String group_id,
      String instrumentModel,
      Tuple ius,
      String kit,
      String library_design,
      String library_name,
      long library_size,
      String library_type,
      Tuple lims,
      String organism,
      Path path,
      String project,
      Optional<String> reference_slide_id,
      Optional<Double> rin,
      long run_id,
      long run_lane_count,
      String run_status,
      String sequencing_control_type,
      Optional<String> sequencing_kit,
      String sequencing_workflow,
      Optional<String> sex,
      Optional<String> spike_in,
      Optional<String> spike_in_dilution_factor,
      Optional<Double> spike_in_volume_ul,
      Instant startDate,
      Optional<String> subproject,
      Optional<Double> target_cell_recovery,
      String tissue_name,
      String tissue_type,
      String tissue_origin,
      String tissue_prep,
      String targeted_resequencing,
      String tissue_region,
      Instant timestamp,
      boolean umis,
      boolean is_sample,
      boolean skip) {
    super(
        barcode_kit,
        bases_mask,
        batches,
        cell_viability,
        completed_date,
        container_model,
        donor,
        dv200,
        external_donor_id,
        external_key,
        external_tissue_id,
        flowcellGeometry,
        group_desc,
        group_id,
        instrumentModel,
        ius,
        kit,
        library_design,
        library_name,
        library_size,
        library_type,
        lims,
        organism,
        path,
        project,
        reference_slide_id,
        rin,
        run_id,
        run_lane_count,
        run_status,
        sequencing_control_type,
        sequencing_kit,
        sequencing_workflow,
        sex,
        spike_in,
        spike_in_dilution_factor,
        spike_in_volume_ul,
        startDate,
        subproject,
        target_cell_recovery,
        tissue_name,
        tissue_type,
        tissue_origin,
        tissue_prep,
        targeted_resequencing,
        tissue_region,
        timestamp,
        umis,
        is_sample);
    this.skip = skip;
  }

  @Override
  public boolean equals(Object o) {
    PineryIUSIncludeSkippedValue that = (PineryIUSIncludeSkippedValue) o;
    return getClass() == o.getClass() && super.equals(o) && skip == that.skip();
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), skip);
  }
}
