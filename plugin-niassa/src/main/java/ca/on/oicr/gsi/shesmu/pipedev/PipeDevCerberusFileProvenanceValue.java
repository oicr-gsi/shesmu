package ca.on.oicr.gsi.shesmu.pipedev;

import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * The information available to Shesmu scripts for processing
 *
 * <p>This is one “row” in the information being fed into Shesmu
 */
public final class PipeDevCerberusFileProvenanceValue implements CerberusFileProvenanceValue {
  private final String accession;
  private final Optional<String> barcode_kit;
  private final Optional<Double> cell_viability;
  private final Instant completed_date;
  private final String donor;
  private final String external_donor_id;
  private final Tuple external_key;
  private final String external_tissue_id;
  private final Map<String, Set<String>> file_attributes;
  private final long file_size;
  private final String group_desc;
  private final String group_id;
  private final Set<String> inputFiles;
  private final String instrument_model;
  private final Tuple ius;
  private final String kit;
  private final String library_design;
  private final String library_name;
  private final long library_size;
  private final String library_type;
  private final Tuple lims;
  private final String md5;
  private final String metatype;
  private final String organism;
  private final Path path;
  private final String project;
  private final Optional<String> reference_slide_id;
  private final String sequencing_control_type;
  private final Optional<String> sex;
  private final Optional<String> spike_in;
  private final Optional<String> spike_in_dilution_factor;
  private final Optional<Double> spike_in_volume_ul;
  private final boolean stale;
  private final Optional<Double> target_cell_recovery;
  private final String targeted_resequencing;
  private final Instant timestamp;
  private final String tissue_name;
  private final String tissue_origin;
  private final String tissue_prep;
  private final String tissue_region;
  private final String tissue_type;
  private final boolean umis;
  private final String workflow;
  private final String workflow_accession;
  private final String workflow_run_accession;
  private final Map<String, Set<String>> workflow_run_attributes;
  private final Tuple workflow_version;

  public PipeDevCerberusFileProvenanceValue(
      String accession,
      Optional<String> barcode_kit,
      Optional<Double> cell_viability,
      Instant completed_date,
      String donor,
      String external_donor_id,
      Tuple external_key,
      String external_tissue_id,
      SortedMap<String, SortedSet<String>> file_attributes,
      long file_size,
      String group_desc,
      String group_id,
      String instrument_model,
      Set<String> inputFiles,
      Tuple ius,
      String kit,
      String library_design,
      String library_name,
      long library_size,
      String library_type,
      Tuple lims,
      String metatype,
      String md5,
      String organism,
      Path path,
      String project,
      Optional<String> reference_slide_id,
      String sequencing_control_type,
      Optional<String> sex,
      Optional<String> spike_in,
      Optional<String> spike_in_dilution_factor,
      Optional<Double> spike_in_volume_ul,
      boolean stale,
      Optional<Double> target_cell_recovery,
      String targeted_resequencing,
      Instant timestamp,
      String tissue_name,
      String tissue_origin,
      String tissue_prep,
      String tissue_region,
      String tissue_type,
      boolean umis,
      String workflow,
      String workflow_accession,
      SortedMap<String, SortedSet<String>> workflow_attributes,
      String workflow_run_accession,
      SortedMap<String, SortedSet<String>> workflow_run_attributes,
      Tuple workflow_version) {
    super();
    this.accession = accession;
    this.barcode_kit = barcode_kit;
    this.cell_viability = cell_viability;
    this.completed_date = completed_date;
    this.donor = donor;
    this.external_donor_id = external_donor_id;
    this.external_key = external_key;
    this.external_tissue_id = external_tissue_id;
    this.file_attributes = IUSUtils.attributes(file_attributes);
    this.file_size = file_size;
    this.group_desc = group_desc;
    this.group_id = group_id;
    this.inputFiles = inputFiles;
    this.instrument_model = instrument_model;
    this.ius = ius;
    this.kit = kit;
    this.library_design = library_design;
    this.library_name = library_name;
    this.library_size = library_size;
    this.library_type = library_type;
    this.lims = lims;
    this.md5 = md5;
    this.metatype = metatype;
    this.organism = organism;
    this.path = path;
    this.project = project;
    this.reference_slide_id = reference_slide_id;
    this.sequencing_control_type = sequencing_control_type;
    this.sex = sex;
    this.spike_in = spike_in;
    this.spike_in_dilution_factor = spike_in_dilution_factor;
    this.spike_in_volume_ul = spike_in_volume_ul;
    this.stale = stale;
    this.target_cell_recovery = target_cell_recovery;
    this.targeted_resequencing = targeted_resequencing;
    this.timestamp = timestamp;
    this.tissue_name = tissue_name;
    this.tissue_origin = tissue_origin;
    this.tissue_prep = tissue_prep;
    this.tissue_region = tissue_region;
    this.tissue_type = tissue_type;
    this.umis = umis;
    this.workflow = workflow;
    this.workflow_accession = workflow_accession;
    this.workflow_run_accession = workflow_run_accession;
    this.workflow_version = workflow_version;
    final SortedMap<String, Set<String>> mergedAttributes = new TreeMap<>(workflow_attributes);
    mergedAttributes.putAll(workflow_run_attributes);
    this.workflow_run_attributes = mergedAttributes;
  }

  @Override
  public String accession() {
    return accession;
  }

  @Override
  public Optional<String> barcode_kit() {
    return barcode_kit;
  }

  @Override
  public Optional<Double> cell_viability() {
    return cell_viability;
  }

  @Override
  public Instant completed_date() {
    return completed_date;
  }

  @Override
  public String donor() {
    return donor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PipeDevCerberusFileProvenanceValue that = (PipeDevCerberusFileProvenanceValue) o;
    return file_size == that.file_size
        && library_size == that.library_size
        && stale == that.stale
        && umis == that.umis
        && accession.equals(that.accession)
        && barcode_kit.equals(that.barcode_kit)
        && cell_viability.equals(that.cell_viability)
        && completed_date.equals(that.completed_date)
        && donor.equals(that.donor)
        && external_donor_id.equals(that.external_donor_id)
        && external_key.equals(that.external_key)
        && external_tissue_id.equals(that.external_tissue_id)
        && file_attributes.equals(that.file_attributes)
        && group_desc.equals(that.group_desc)
        && group_id.equals(that.group_id)
        && inputFiles.equals(that.inputFiles)
        && instrument_model.equals(that.instrument_model)
        && ius.equals(that.ius)
        && kit.equals(that.kit)
        && library_design.equals(that.library_design)
        && library_name.equals(that.library_name)
        && library_type.equals(that.library_type)
        && lims.equals(that.lims)
        && md5.equals(that.md5)
        && metatype.equals(that.metatype)
        && organism.equals(that.organism)
        && path.equals(that.path)
        && project.equals(that.project)
        && reference_slide_id.equals(that.reference_slide_id)
        && sequencing_control_type.equals(that.sequencing_control_type)
        && sex.equals(that.sex)
        && spike_in.equals(that.spike_in)
        && spike_in_dilution_factor.equals(that.spike_in_dilution_factor)
        && spike_in_volume_ul.equals(that.spike_in_volume_ul)
        && target_cell_recovery.equals(that.target_cell_recovery)
        && targeted_resequencing.equals(that.targeted_resequencing)
        && timestamp.equals(that.timestamp)
        && tissue_name.equals(that.tissue_name)
        && tissue_origin.equals(that.tissue_origin)
        && tissue_prep.equals(that.tissue_prep)
        && tissue_region.equals(that.tissue_region)
        && tissue_type.equals(that.tissue_type)
        && workflow.equals(that.workflow)
        && workflow_accession.equals(that.workflow_accession)
        && workflow_run_accession.equals(that.workflow_run_accession)
        && workflow_run_attributes.equals(that.workflow_run_attributes)
        && workflow_version.equals(that.workflow_version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        accession,
        barcode_kit,
        cell_viability,
        completed_date,
        donor,
        external_donor_id,
        external_key,
        external_tissue_id,
        file_attributes,
        file_size,
        group_desc,
        group_id,
        inputFiles,
        instrument_model,
        ius,
        kit,
        library_design,
        library_name,
        library_size,
        library_type,
        lims,
        md5,
        metatype,
        organism,
        path,
        project,
        reference_slide_id,
        sequencing_control_type,
        sex,
        spike_in,
        spike_in_dilution_factor,
        spike_in_volume_ul,
        stale,
        target_cell_recovery,
        targeted_resequencing,
        timestamp,
        tissue_name,
        tissue_origin,
        tissue_prep,
        tissue_region,
        tissue_type,
        umis,
        workflow,
        workflow_accession,
        workflow_run_accession,
        workflow_run_attributes,
        workflow_version);
  }

  @Override
  public Map<String, Set<String>> file_attributes() {
    return file_attributes;
  }

  @Override
  public long file_size() {
    return file_size;
  }

  @Override
  public String group_desc() {
    return group_desc;
  }

  @Override
  public String group_id() {
    return group_id;
  }

  @Override
  public Set<String> input_files() {
    return inputFiles;
  }

  @Override
  public String instrument_model() {
    return instrument_model;
  }

  @Override
  public Tuple ius() {
    return ius;
  }

  @Override
  public String kit() {
    return kit;
  }

  @Override
  public String library_design() {
    return library_design;
  }

  @Override
  public String library_name() {
    return library_name;
  }

  @Override
  public long library_size() {
    return library_size;
  }

  @Override
  public String library_type() {
    return library_type;
  }

  @Override
  public Tuple lims() {
    return lims;
  }

  @Override
  public String md5() {
    return md5;
  }

  @Override
  public String metatype() {
    return metatype;
  }

  @Override
  public String organism() {
    return organism;
  }

  @Override
  public Path path() {
    return path;
  }

  @Override
  public String project() {
    return project;
  }

  @Override
  public Optional<String> reference_slide_id() {
    return reference_slide_id;
  }

  @Override
  public String sequencing_control_type() {
    return sequencing_control_type;
  }

  @Override
  public Optional<String> sex() {
    return sex;
  }

  @Override
  public Optional<String> spike_in() {
    return spike_in;
  }

  @Override
  public Optional<String> spike_in_dilution_factor() {
    return spike_in_dilution_factor;
  }

  @Override
  public Optional<Double> spike_in_volume_ul() {
    return spike_in_volume_ul;
  }

  @Override
  public boolean stale() {
    return stale;
  }

  @Override
  public Optional<Double> target_cell_recovery() {
    return target_cell_recovery;
  }

  @Override
  public String targeted_resequencing() {
    return targeted_resequencing;
  }

  @Override
  public Instant timestamp() {
    return timestamp;
  }

  @Override
  public String tissue_name() {
    return tissue_name;
  }

  @Override
  public String tissue_origin() {
    return tissue_origin;
  }

  @Override
  public String tissue_prep() {
    return tissue_prep;
  }

  @Override
  public String tissue_region() {
    return tissue_region;
  }

  @Override
  public String tissue_type() {
    return tissue_type;
  }

  @Override
  public String external_donor_id() {
    return external_donor_id;
  }

  @Override
  public Tuple external_key() {
    return external_key;
  }

  @Override
  public String external_tissue_id() {
    return external_tissue_id;
  }

  @Override
  public boolean umis() {
    return umis;
  }

  @Override
  public String workflow() {
    return workflow;
  }

  @Override
  public String workflow_accession() {
    return workflow_accession;
  }

  @Override
  public AlgebraicValue workflow_engine() {
    return new AlgebraicValue("NIASSA");
  }

  @Override
  public String workflow_run_accession() {
    return workflow_run_accession;
  }

  @Override
  public Map<String, Set<String>> workflow_run_attributes() {
    return workflow_run_attributes;
  }

  @Override
  public Map<String, JsonNode> workflow_run_labels() {
    return Collections.emptyMap();
  }

  @Override
  public Tuple workflow_version() {
    return workflow_version;
  }
}
