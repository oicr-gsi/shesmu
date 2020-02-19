package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.Gang;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * The information available to Shesmu scripts for processing
 *
 * <p>This is one “row” in the information being fed into Shesmu
 */
public final class CerberusFileProvenanceValue {
  private final String accession;
  private final Optional<Double> cell_viability;
  private final Instant completed_date;
  private final String donor;
  private final String external_name;
  private Set<Tuple> file_attributes;
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
  private final Set<Tuple> workflow_run_attributes;
  private final Tuple workflow_version;

  public CerberusFileProvenanceValue(
      String accession,
      Optional<Double> cell_viability,
      Instant completed_date,
      String donor,
      String external_name,
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
    this.cell_viability = cell_viability;
    this.path = path;
    this.metatype = metatype;
    this.md5 = md5;
    this.file_size = file_size;
    this.umis = umis;
    this.workflow = workflow;
    this.workflow_accession = workflow_accession;
    this.workflow_run_accession = workflow_run_accession;
    this.workflow_version = workflow_version;
    this.project = project;
    this.organism = organism;
    this.library_name = library_name;
    this.donor = donor;
    this.external_name = external_name;
    this.ius = ius;
    this.library_design = library_design;
    this.tissue_type = tissue_type;
    this.tissue_name = tissue_name;
    this.tissue_origin = tissue_origin;
    this.tissue_prep = tissue_prep;
    this.targeted_resequencing = targeted_resequencing;
    this.tissue_region = tissue_region;
    this.group_id = group_id;
    this.group_desc = group_desc;
    this.library_size = library_size;
    this.library_type = library_type;
    this.kit = kit;
    this.timestamp = timestamp;
    this.lims = lims;
    this.completed_date = completed_date;
    this.stale = stale;
    this.file_attributes = IUSUtils.attributes(file_attributes);
    this.reference_slide_id = reference_slide_id;
    this.spike_in = spike_in;
    this.spike_in_dilution_factor = spike_in_dilution_factor;
    this.spike_in_volume_ul = spike_in_volume_ul;
    this.target_cell_recovery = target_cell_recovery;
    final SortedMap<String, SortedSet<String>> mergedAttributes =
        new TreeMap<>(workflow_attributes);
    mergedAttributes.putAll(workflow_run_attributes);
    this.workflow_run_attributes = IUSUtils.attributes(mergedAttributes);
    this.instrument_model = instrument_model;
    this.inputFiles = inputFiles;
    this.sex = sex;
  }

  @ShesmuVariable
  public String accession() {
    return accession;
  }

  @ShesmuVariable(signable = true)
  public Optional<Double> cell_viability() {
    return cell_viability;
  }

  @ShesmuVariable
  public Instant completed_date() {
    return completed_date;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library", order = 0)})
  public String donor() {
    return donor;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CerberusFileProvenanceValue that = (CerberusFileProvenanceValue) o;
    return file_size == that.file_size
        && library_size == that.library_size
        && stale == that.stale
        && accession.equals(that.accession)
        && cell_viability.equals(that.cell_viability)
        && completed_date.equals(that.completed_date)
        && donor.equals(that.donor)
        && external_name.equals(that.external_name)
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
        && umis == that.umis
        && workflow.equals(that.workflow)
        && workflow_accession.equals(that.workflow_accession)
        && workflow_run_accession.equals(that.workflow_run_accession)
        && workflow_run_attributes.equals(that.workflow_run_attributes)
        && workflow_version.equals(that.workflow_version);
  }

  @ShesmuVariable(signable = true)
  public String external_name() {
    return external_name;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> file_attributes() {
    return file_attributes;
  }

  @ShesmuVariable
  public long file_size() {
    return file_size;
  }

  @ShesmuVariable(signable = true)
  public String group_desc() {
    return group_desc;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library", order = 4, dropIfDefault = true)})
  public String group_id() {
    return group_id;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        accession,
        cell_viability,
        completed_date,
        donor,
        external_name,
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

  @ShesmuVariable
  public Set<String> input_files() {
    return inputFiles;
  }

  @ShesmuVariable(signable = true)
  public String instrument_model() {
    return instrument_model;
  }

  @ShesmuVariable(type = "t3sis")
  public Tuple ius() {
    return ius;
  }

  @ShesmuVariable(signable = true)
  public String kit() {
    return kit;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library", order = 3)})
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

  @ShesmuVariable
  public String md5() {
    return md5;
  }

  @ShesmuVariable
  public String metatype() {
    return metatype;
  }

  @ShesmuVariable(signable = true)
  public String organism() {
    return organism;
  }

  @ShesmuVariable
  public Path path() {
    return path;
  }

  @ShesmuVariable(signable = true)
  public String project() {
    return project;
  }

  @ShesmuVariable(signable = true)
  public Optional<String> reference_slide_id() {
    return reference_slide_id;
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

  @ShesmuVariable
  public boolean stale() {
    return stale;
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

  @ShesmuVariable(signable = true)
  public String tissue_name() {
    return tissue_name;
  }

  @ShesmuVariable(
      signable = true,
      gangs = {@Gang(name = "merged_library", order = 1)})
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
      gangs = {@Gang(name = "merged_library", order = 2)})
  public String tissue_type() {
    return tissue_type;
  }

  @ShesmuVariable(signable = true)
  public boolean umis() {
    return umis;
  }

  @ShesmuVariable
  public String workflow() {
    return workflow;
  }

  @ShesmuVariable
  public String workflow_accession() {
    return workflow_accession;
  }

  @ShesmuVariable
  public String workflow_run_accession() {
    return workflow_run_accession;
  }

  @ShesmuVariable(type = "at2sas")
  public Set<Tuple> workflow_run_attributes() {
    return workflow_run_attributes;
  }

  @ShesmuVariable(type = "t3iii")
  public Tuple workflow_version() {
    return workflow_version;
  }
}
