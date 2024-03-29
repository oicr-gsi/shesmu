package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.Gang;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class PineryIUSValue {
  private final Optional<String> barcode_kit;
  private final String bases_mask;
  private final Set<String> batches;
  private final Optional<Double> cell_viability;
  private final Optional<Instant> completed_date;
  private final Optional<String> container_model;
  private final String donor;
  private final Optional<Double> dv200;
  private final String external_donor_id;
  private final Tuple external_key;
  private final String external_tissue_id;
  private final Set<Set<Long>> flowcellGeometry;
  private final String group_desc;
  private final String group_id;
  private final String instrumentModel;
  private final boolean is_sample;
  private final Tuple ius;
  private final String kit;
  private final String library_design;
  private final String library_name;
  private final long library_size;
  private final String library_type;
  private final Tuple lims;
  private final String organism;
  private final Path path;
  private final String project;
  private final Optional<String> reference_slide_id;
  private final Optional<Double> rin;
  private final long run_id;
  private final long run_lane_count;
  private final String run_status;
  private final String sequencing_control_type;
  private final Optional<String> sequencing_kit;
  private final String sequencing_workflow;
  private final Optional<String> sex;
  private final Optional<String> spike_in;
  private final Optional<String> spike_in_dilution_factor;
  private final Optional<Double> spike_in_volume_ul;
  private final Instant startDate;
  private final Optional<String> subproject;
  private final Optional<Double> target_cell_recovery;
  private final String targeted_resequencing;
  private final Instant timestamp;
  private final String tissue_name;
  private final String tissue_origin;
  private final String tissue_prep;
  private final String tissue_region;
  private final String tissue_type;
  private final boolean umis;

  public PineryIUSValue(
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
      boolean is_sample) {
    super();
    this.barcode_kit = barcode_kit;
    this.bases_mask = bases_mask;
    this.batches = batches;
    this.cell_viability = cell_viability;
    this.completed_date = completed_date;
    this.container_model = container_model;
    this.donor = donor;
    this.dv200 = dv200;
    this.external_donor_id = external_donor_id;
    this.external_key = external_key;
    this.external_tissue_id = external_tissue_id;
    this.flowcellGeometry = flowcellGeometry;
    this.group_desc = group_desc;
    this.group_id = group_id;
    this.instrumentModel = instrumentModel;
    this.run_id = run_id;
    this.run_lane_count = run_lane_count;
    this.subproject = subproject;
    this.is_sample = is_sample;
    this.ius = ius;
    this.kit = kit;
    this.library_design = library_design;
    this.library_name = library_name;
    this.library_size = library_size;
    this.library_type = library_type;
    this.lims = lims;
    this.organism = organism;
    this.path = path;
    this.project = project;
    this.reference_slide_id = reference_slide_id;
    this.rin = rin;
    this.run_status = run_status;
    this.sequencing_control_type = sequencing_control_type;
    this.sequencing_kit = sequencing_kit;
    this.sequencing_workflow = sequencing_workflow;
    this.sex = sex;
    this.spike_in = spike_in;
    this.spike_in_dilution_factor = spike_in_dilution_factor;
    this.spike_in_volume_ul = spike_in_volume_ul;
    this.startDate = startDate;
    this.target_cell_recovery = target_cell_recovery;
    this.targeted_resequencing = targeted_resequencing;
    this.timestamp = timestamp;
    this.tissue_name = tissue_name;
    this.tissue_origin = tissue_origin;
    this.tissue_prep = tissue_prep;
    this.tissue_region = tissue_region;
    this.tissue_type = tissue_type;
    this.umis = umis;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> barcode_kit() {
    return barcode_kit;
  }

  @ShesmuVariable(signable = true)
  public String bases_mask() {
    return bases_mask;
  }

  @ShesmuVariable
  public Set<String> batches() {
    return batches;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> cell_viability() {
    return cell_viability;
  }

  @ShesmuVariable
  public Optional<Instant> completed_date() {
    return completed_date;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> container_model() {
    return container_model;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 0),
        @Gang(name = "merged_library", order = 0)
      })
  public String donor() {
    return donor;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> dv200() {
    return dv200;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PineryIUSValue that = (PineryIUSValue) o;
    return is_sample == that.is_sample()
        && library_size == that.library_size()
        && run_id == that.run_id()
        && run_lane_count == that.run_lane_count()
        && umis == that.umis()
        && barcode_kit.equals(that.barcode_kit())
        && bases_mask.equals(that.bases_mask())
        && batches.equals(that.batches())
        && cell_viability.equals(that.cell_viability())
        && completed_date.equals(that.completed_date())
        && container_model.equals(that.container_model())
        && donor.equals(that.donor())
        && dv200.equals(that.dv200())
        && external_donor_id.equals(that.external_donor_id())
        && external_key.equals(that.external_key())
        && external_tissue_id.equals(that.external_tissue_id())
        && flowcellGeometry.equals(that.flowcell_geometry())
        && group_desc.equals(that.group_desc())
        && group_id.equals(that.group_id())
        && instrumentModel.equals(that.instrument_model())
        && ius.equals(that.ius())
        && kit.equals(that.kit())
        && library_design.equals(that.library_design())
        && library_name.equals(that.library_name())
        && library_type.equals(that.library_type())
        && lims.equals(that.lims())
        && organism.equals(that.organism())
        && path.equals(that.path())
        && project.equals(that.project())
        && reference_slide_id.equals(that.reference_slide_id())
        && rin.equals(that.rin())
        && run_status.equals(that.run_status())
        && sequencing_control_type.equals(that.sequencing_control_type())
        && sequencing_kit.equals(that.sequencing_kit())
        && sequencing_workflow.equals(that.sequencing_workflow())
        && sex.equals(that.sex())
        && spike_in.equals(that.spike_in())
        && spike_in_dilution_factor.equals(that.spike_in_dilution_factor())
        && spike_in_volume_ul.equals(that.spike_in_volume_ul())
        && startDate.equals(that.start_date())
        && subproject.equals(that.subproject())
        && target_cell_recovery.equals(that.target_cell_recovery())
        && targeted_resequencing.equals(that.targeted_resequencing())
        && timestamp.equals(that.timestamp())
        && tissue_name.equals(that.tissue_name())
        && tissue_origin.equals(that.tissue_origin())
        && tissue_prep.equals(that.tissue_prep())
        && tissue_region.equals(that.tissue_region())
        && tissue_type.equals(that.tissue_type());
  }

  @ShesmuVariable(signable = true)
  public String external_donor_id() {
    return external_donor_id;
  }

  @ShesmuVariable(type = "o4id$sprovider$sstale$bversions$mss")
  public Tuple external_key() {
    return external_key;
  }

  @ShesmuVariable(signable = true)
  public String external_tissue_id() {
    return external_tissue_id;
  }

  @ShesmuVariable
  public Set<Set<Long>> flowcell_geometry() {
    return flowcellGeometry;
  }

  @ShesmuVariable(signable = true)
  public String group_desc() {
    return group_desc;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library", order = 4, dropIfDefault = true),
        @Gang(name = "merged_library_legacy", order = 4, dropIfDefault = true),
        @Gang(name = "merged_library_new", order = 3, dropIfDefault = true)
      })
  public String group_id() {
    return group_id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
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
        is_sample,
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
        targeted_resequencing,
        timestamp,
        tissue_name,
        tissue_origin,
        tissue_prep,
        tissue_region,
        tissue_type,
        umis);
  }

  @ShesmuVariable(signable = true)
  public String instrument_model() {
    return instrumentModel;
  }

  @ShesmuVariable
  public boolean is_sample() {
    return is_sample;
  }

  @ShesmuVariable(type = "t3sis", signable = true)
  public Tuple ius() {
    return ius;
  }

  @ShesmuVariable(signable = true)
  public String kit() {
    return kit;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 3),
        @Gang(name = "merged_library", order = 3),
        @Gang(name = "merged_library_new", order = 2)
      })
  public String library_design() {
    return library_design;
  }

  @ShesmuVariable(signable = true)
  public String library_name() {
    return library_name;
  }

  @ShesmuVariable(signable = true)
  public long library_size() {
    return library_size;
  }

  @ShesmuVariable(signable = true)
  public String library_type() {
    return library_type;
  }

  @ShesmuVariable(type = "o4id$sprovider$stime$dversion$s")
  public Tuple lims() {
    return lims;
  }

  @ShesmuVariable(signable = true)
  public String organism() {
    return organism;
  }

  @ShesmuVariable
  public Path path() {
    return path;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library_new", order = 0)})
  public String project() {
    return project;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> reference_slide_id() {
    return reference_slide_id;
  }

  @ShesmuVariable
  public Optional<Double> rin() {
    return rin;
  }

  @ShesmuVariable
  public long run_id() {
    return run_id;
  }

  @ShesmuVariable
  public long run_lane_count() {
    return run_lane_count;
  }

  @ShesmuVariable(signable = true)
  public String run_name() {
    return (String) ius.get(0);
  }

  @ShesmuVariable
  public String run_status() {
    return run_status;
  }

  @ShesmuVariable(signable = true)
  public String sequencing_control_type() {
    return sequencing_control_type;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> sequencing_kit() {
    return sequencing_kit;
  }

  @ShesmuVariable(signable = true)
  public String sequencing_workflow() {
    return sequencing_workflow;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> sex() {
    return sex;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> spike_in() {
    return spike_in;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> spike_in_dilution_factor() {
    return spike_in_dilution_factor;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> spike_in_volume_ul() {
    return spike_in_volume_ul;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> subproject() {
    return subproject;
  }

  @ShesmuVariable
  public Instant start_date() {
    return startDate;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> target_cell_recovery() {
    return target_cell_recovery;
  }

  @ShesmuVariable(signable = true)
  public String targeted_resequencing() {
    return targeted_resequencing;
  }

  @ShesmuVariable
  public Instant timestamp() {
    return timestamp;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library_new", order = 1)})
  public String tissue_name() {
    return tissue_name;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 1),
        @Gang(name = "merged_library", order = 1)
      })
  public String tissue_origin() {
    return tissue_origin;
  }

  @ShesmuVariable(signable = true)
  public String tissue_prep() {
    return tissue_prep;
  }

  @ShesmuVariable(signable = true)
  public String tissue_region() {
    return tissue_region;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {
        @Gang(name = "merged_library_legacy", order = 2),
        @Gang(name = "merged_library", order = 2)
      })
  public String tissue_type() {
    return tissue_type;
  }

  @ShesmuVariable(signable = true)
  public boolean umis() {
    return umis;
  }
}
